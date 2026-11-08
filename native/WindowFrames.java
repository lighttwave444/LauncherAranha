package com.launchertop.aranha;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Desenha a moldura do launcher (barra de titulo, X e canto de redimensionar) por cima da janela real do app. */
public class WindowFrames {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Frame> FRAMES = new HashMap<String, Frame>();
    private static int count = 0;
    private static boolean watching = false;

    public static boolean has(String pkg) {
        synchronized (FRAMES) { return FRAMES.containsKey(pkg); }
    }

    /** Chamado depois que o app foi aberto em janela: acha a janela, define o tamanho e coloca a moldura. */
    public static void attach(final Context ctx, final String pkg) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (!ensureOverlayPermission(ctx)) return;
                    int id = -1;
                    for (int i = 0; i < 8 && id < 0; i++) {
                        Thread.sleep(450);
                        id = findTask(pkg);
                    }
                    if (id < 0) return;
                    final Rect b = initialBounds(ctx);
                    RootShell.send("am task resize " + id + " " + b.left + " " + b.top + " " + b.right + " " + b.bottom);
                    final int tid = id;
                    MAIN.post(new Runnable() {
                        public void run() {
                            try { create(ctx.getApplicationContext(), pkg, tid, b); } catch (Exception e) { }
                        }
                    });
                } catch (Exception e) { }
            }
        }).start();
    }

    private static boolean ensureOverlayPermission(Context ctx) throws Exception {
        if (Build.VERSION.SDK_INT < 23) return true;
        if (Settings.canDrawOverlays(ctx)) return true;
        RootShell.run("appops set " + ctx.getPackageName() + " SYSTEM_ALERT_WINDOW allow");
        Thread.sleep(300);
        if (Settings.canDrawOverlays(ctx)) return true;
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
        return false;
    }

    private static int findTask(String pkg) {
        String out = RootShell.run("dumpsys activity activities | grep 'Task{'");
        Pattern p = Pattern.compile("#(\\d+) [^\\n]*" + Pattern.quote(pkg));
        Matcher m = p.matcher(out);
        if (m.find()) return Integer.parseInt(m.group(1));
        return -1;
    }

    private static Rect initialBounds(Context ctx) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;
        int off = (count % 4) * dp(ctx, 14);
        int bar = dp(ctx, 40);
        int left = (int) (w * 0.06) + off;
        int top = (int) (h * 0.13) + bar + off * 2;
        return new Rect(left, top, left + (int) (w * 0.88), top + (int) (h * 0.48));
    }

    private static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int overlayType() {
        return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private static WindowManager.LayoutParams lp(int w, int h) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w, h, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        return p;
    }

    private static class Frame {
        Context ctx;
        WindowManager wm;
        String pkg;
        int id;
        Rect b;
        LinearLayout bar;
        TextView grip;
        WindowManager.LayoutParams barLp, gripLp;
        int barH, gripS;
        long lastSend = 0;

        void layout() {
            barLp.x = b.left;
            barLp.y = b.top - barH;
            barLp.width = b.width();
            gripLp.x = b.right - gripS;
            gripLp.y = b.bottom - gripS;
            try { wm.updateViewLayout(bar, barLp); } catch (Exception e) { }
            try { wm.updateViewLayout(grip, gripLp); } catch (Exception e) { }
        }

        void push(boolean force) {
            long now = System.currentTimeMillis();
            if (!force && now - lastSend < 70) return;
            lastSend = now;
            RootShell.send("am task resize " + id + " " + b.left + " " + b.top + " " + b.right + " " + b.bottom);
        }

        void close(boolean kill) {
            if (kill) RootShell.send("am force-stop " + pkg);
            remove();
        }

        void remove() {
            try { wm.removeView(bar); } catch (Exception e) { }
            try { wm.removeView(grip); } catch (Exception e) { }
            synchronized (FRAMES) { FRAMES.remove(pkg); }
        }
    }

    private static void create(final Context ctx, final String pkg, final int id, Rect bounds) {
        if (has(pkg)) return;
        final Frame f = new Frame();
        f.ctx = ctx;
        f.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        f.pkg = pkg;
        f.id = id;
        f.b = new Rect(bounds);
        f.barH = dp(ctx, 40);
        f.gripS = dp(ctx, 36);
        count++;

        String label = pkg;
        try {
            PackageManager pm = ctx.getPackageManager();
            label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) { }

        // barra de titulo (vidro escuro, como no launcher)
        f.bar = new LinearLayout(ctx);
        f.bar.setOrientation(LinearLayout.HORIZONTAL);
        f.bar.setGravity(Gravity.CENTER_VERTICAL);
        float r = dp(ctx, 14);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF22A2D3A, 0xF21B1D26});
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke(dp(ctx, 1), 0x88FFFFFF);
        f.bar.setBackground(bg);

        TextView title = new TextView(ctx);
        title.setText(label);
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(ctx, 14), 0, dp(ctx, 8), 0);
        f.bar.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView x = new TextView(ctx);
        x.setText("\u2715");
        x.setTextColor(Color.WHITE);
        x.setTextSize(13);
        x.setGravity(Gravity.CENTER);
        GradientDrawable xb = new GradientDrawable();
        xb.setShape(GradientDrawable.OVAL);
        xb.setColor(0xFFFF5F57);
        x.setBackground(xb);
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(dp(ctx, 26), dp(ctx, 26));
        xl.rightMargin = dp(ctx, 8);
        f.bar.addView(x, xl);
        x.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { f.close(true); }
        });

        // canto para redimensionar
        f.grip = new TextView(ctx);
        f.grip.setText("\u25E2");
        f.grip.setTextColor(0xCCFFFFFF);
        f.grip.setTextSize(22);
        f.grip.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        f.grip.setPadding(0, 0, dp(ctx, 4), 0);

        f.barLp = lp(f.b.width(), f.barH);
        f.gripLp = lp(f.gripS, f.gripS);

        f.bar.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy;
            Rect start;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY();
                        start = new Rect(f.b);
                        RootShell.send("am task focus " + f.id);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int mx = (int) (e.getRawX() - sx), my = (int) (e.getRawY() - sy);
                        int top = Math.max(f.barH, start.top + my);
                        int dy = top - start.top;
                        f.b.set(start.left + mx, start.top + dy, start.right + mx, start.bottom + dy);
                        f.layout();
                        f.push(false);
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        f.push(true);
                        return true;
                }
                return false;
            }
        });

        f.grip.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy;
            Rect start;
            public boolean onTouch(View v, MotionEvent e) {
                int min = dp(ctx, 200);
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY();
                        start = new Rect(f.b);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int nr = Math.max(start.left + min, start.right + (int) (e.getRawX() - sx));
                        int nb = Math.max(start.top + min, start.bottom + (int) (e.getRawY() - sy));
                        f.b.set(start.left, start.top, nr, nb);
                        f.layout();
                        f.push(false);
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        f.push(true);
                        return true;
                }
                return false;
            }
        });

        f.layout();
        f.barLp.x = f.b.left;
        f.barLp.y = f.b.top - f.barH;
        f.gripLp.x = f.b.right - f.gripS;
        f.gripLp.y = f.b.bottom - f.gripS;
        f.wm.addView(f.bar, f.barLp);
        f.wm.addView(f.grip, f.gripLp);
        synchronized (FRAMES) { FRAMES.put(pkg, f); }
        startWatch();
    }

    /** Se o app for fechado por outro meio, tira a moldura da tela. */
    private static void startWatch() {
        synchronized (FRAMES) {
            if (watching) return;
            watching = true;
        }
        new Thread(new Runnable() {
            public void run() {
                try {
                    while (true) {
                        Thread.sleep(4000);
                        List<Frame> list;
                        synchronized (FRAMES) {
                            if (FRAMES.isEmpty()) { watching = false; return; }
                            list = new ArrayList<Frame>(FRAMES.values());
                        }
                        for (final Frame f : list) {
                            if (findTask(f.pkg) < 0) {
                                MAIN.post(new Runnable() {
                                    public void run() { f.remove(); }
                                });
                            }
                        }
                    }
                } catch (Exception e) {
                    synchronized (FRAMES) { watching = false; }
                }
            }
        }).start();
    }
}
