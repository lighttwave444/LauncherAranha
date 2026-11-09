package com.launchertop.aranha;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Janela propria do launcher: o app roda num display virtual e a imagem dele e desenhada
 * dentro da moldura (barra de titulo, X e canto de redimensionar).
 * Mover = so mexer na moldura. Redimensionar = estica a imagem e, ao soltar, ajusta o display de verdade.
 * Log de diagnostico: /sdcard/Download/aranha-vd.log
 */
public class VirtualWindow {
    private static final String TAG = "AranhaVD";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, VirtualWindow> WINDOWS = new HashMap<String, VirtualWindow>();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static Process helper;
    private static volatile OutputStream helperIn;
    private static int count = 0;

    private final Context ctx;
    private final String key, pkg, amArgs;
    private final Runnable fallback;
    private WindowManager wm;
    private WindowManager.LayoutParams wlp;
    private FrameLayout root;
    private TextureView tv;
    private SurfaceTexture st;
    private Surface surface;
    private VirtualDisplay vd;
    private int dispW, dispH, dpi, barH;
    private volatile int displayId = -1;
    private volatile int frames = 0;
    private boolean closed = false;

    private VirtualWindow(Context ctx, String key, String pkg, String amArgs, Runnable fallback) {
        this.ctx = ctx;
        this.key = key;
        this.pkg = pkg;
        this.amArgs = amArgs;
        this.fallback = fallback;
    }

    public static boolean has(String key) {
        synchronized (WINDOWS) { return WINDOWS.containsKey(key); }
    }

    static void log(String s) {
        Log.d(TAG, s);
        try {
            RootShell.send("echo '" + s.replace("'", " ").replace("\n", " ") + "' >> /sdcard/Download/aranha-vd.log");
        } catch (Exception e) { }
    }

    /**
     * Abre o app numa janela propria. Retorna false se nao deu para comecar (ex.: sem permissao de overlay),
     * e ai quem chamou deve usar o metodo antigo. Se falhar depois, roda o fallback sozinho.
     */
    public static boolean open(final Context ctx, final String key, final String pkg,
                               final String amArgs, final Runnable fallback) {
        try {
            if (!Settings.canDrawOverlays(ctx)) {
                RootShell.run("appops set " + ctx.getPackageName() + " SYSTEM_ALERT_WINDOW allow");
                try { Thread.sleep(300); } catch (Exception e) { }
                if (!Settings.canDrawOverlays(ctx)) return false;
            }
        } catch (Exception e) {
            return false;
        }
        MAIN.post(new Runnable() {
            public void run() {
                VirtualWindow w = new VirtualWindow(ctx.getApplicationContext(), key, pkg, amArgs, fallback);
                try {
                    w.show();
                } catch (Throwable t) {
                    log("erro ao criar janela: " + t);
                    w.fail("show: " + t);
                }
            }
        });
        return true;
    }

    private int dp(float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    private void show() {
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        dpi = dm.densityDpi;
        barH = dp(40);
        dispW = ((int) (dm.widthPixels * 0.88)) & ~1;
        dispH = ((int) (dm.heightPixels * 0.48)) & ~1;
        int off = (count++ % 4) * dp(14);

        String label = key;
        if ("dial".equals(key)) {
            label = "Telefone";
        } else {
            try {
                PackageManager pm = ctx.getPackageManager();
                label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            } catch (Exception e) { }
        }

        final float rad = dp(14);
        root = new FrameLayout(ctx);
        GradientDrawable rbg = new GradientDrawable();
        rbg.setColor(0xFF262936);
        rbg.setCornerRadius(rad);
        rbg.setStroke(dp(1), 0x88FFFFFF);
        root.setBackground(rbg);
        root.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad);
            }
        });
        root.setClipToOutline(true);
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or2, int ob) {
                v.invalidateOutline();
            }
        });

        // fundo escuro opaco por baixo da imagem do app (evita area transparente)
        View base = new View(ctx);
        base.setBackgroundColor(0xFF14161D);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        blp.topMargin = barH;
        root.addView(base, blp);

        // imagem do app (display virtual)
        tv = new TextureView(ctx);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        tlp.topMargin = barH;
        root.addView(tv, tlp);
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) {
                st = s;
                st.setDefaultBufferSize(dispW, dispH);
                try {
                    startDisplay();
                } catch (Throwable t) {
                    log("erro display: " + t);
                    fail("display: " + t);
                }
            }
            public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) { }
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
            public void onSurfaceTextureUpdated(SurfaceTexture s) { frames++; }
        });
        tv.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                if (displayId < 0) return true;
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) v.requestUnbufferedDispatch(e);
                float sx = (float) dispW / Math.max(1, v.getWidth());
                float sy = (float) dispH / Math.max(1, v.getHeight());
                StringBuilder sb = new StringBuilder("E ");
                sb.append(e.getAction()).append(' ').append(displayId).append(' ')
                        .append(e.getDownTime()).append(' ').append(e.getEventTime()).append(' ')
                        .append(e.getPointerCount());
                for (int i = 0; i < e.getPointerCount(); i++) {
                    sb.append(' ').append(e.getPointerId(i)).append(' ')
                            .append(e.getX(i) * sx).append(' ').append(e.getY(i) * sy);
                }
                send(sb.toString());
                return true;
            }
        });

        // barra de titulo (vidro escuro)
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xEE3B4052, 0xEE262936}));

        TextView title = new TextView(ctx);
        title.setText(label);
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(14), 0, dp(8), 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView x = new TextView(ctx);
        x.setText("\u2715");
        x.setTextColor(Color.WHITE);
        x.setTextSize(15);
        x.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        x.setGravity(Gravity.CENTER);
        GradientDrawable xb = new GradientDrawable();
        xb.setShape(GradientDrawable.OVAL);
        xb.setColor(0xFFFF5F57);
        x.setBackground(xb);
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(dp(28), dp(28));
        xl.rightMargin = dp(8);
        bar.addView(x, xl);
        x.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { close(true); }
        });
        root.addView(bar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, barH, Gravity.TOP));

        // arrastar pela barra: so mexe na moldura
        final View.OnTouchListener dragL = new View.OnTouchListener() {
            float sx, sy;
            int x0, y0;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY();
                        x0 = wlp.x; y0 = wlp.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        wlp.x = x0 + (int) (e.getRawX() - sx);
                        wlp.y = Math.max(0, y0 + (int) (e.getRawY() - sy));
                        update();
                        return true;
                }
                return false;
            }
        };
        bar.setOnTouchListener(dragL);
        // seta: arrastar por ela move a janela (mesmo listener da barra)
        TextView seta = new TextView(ctx);
        seta.setText("\u2039");
        seta.setTextColor(Color.WHITE);
        seta.setTextSize(22);
        seta.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        seta.setGravity(Gravity.CENTER);
        GradientDrawable setaBg = new GradientDrawable();
        setaBg.setColor(0xCC5A6472);
        setaBg.setCornerRadii(new float[]{0, 0, dp(10), dp(10), dp(10), dp(10), 0, 0});
        seta.setBackground(setaBg);
        seta.setOnTouchListener(dragL);
        root.addView(seta, new FrameLayout.LayoutParams(dp(22), dp(60),
                Gravity.LEFT | Gravity.CENTER_VERTICAL));

        // canto de redimensionar
        GripView grip = new GripView(ctx);
        root.addView(grip, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.BOTTOM | Gravity.RIGHT));
        grip.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy;
            int w0, h0;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY();
                        w0 = wlp.width; h0 = wlp.height;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        wlp.width = Math.max(dp(200), w0 + (int) (e.getRawX() - sx));
                        wlp.height = Math.max(dp(200) + barH, h0 + (int) (e.getRawY() - sy));
                        update();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        applySize();
                        return true;
                }
                return false;
            }
        });

        wlp = new WindowManager.LayoutParams(dispW, dispH + barH,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        wlp.gravity = Gravity.TOP | Gravity.LEFT;
        wlp.x = (int) (dm.widthPixels * 0.06) + off;
        wlp.y = (int) (dm.heightPixels * 0.13) + off * 2;
        wm.addView(root, wlp);
        PointerOverlay.front();
        synchronized (WINDOWS) { WINDOWS.put(key, this); }
    }

    private void update() {
        try { wm.updateViewLayout(root, wlp); } catch (Exception e) { }
    }

    /** Ao soltar o canto: ajusta o display virtual ao tamanho novo para o app se reorganizar nitido. */
    private void applySize() {
        int w = wlp.width & ~1;
        int h = (wlp.height - barH) & ~1;
        if (w == dispW && h == dispH) return;
        dispW = w;
        dispH = h;
        try {
            if (st != null) st.setDefaultBufferSize(w, h);
            if (vd != null) vd.resize(w, h, dpi);
            log("resize " + w + "x" + h);
        } catch (Exception e) {
            log("erro resize: " + e);
        }
    }

    private void startDisplay() {
        surface = new Surface(st);
        RootDisplay.create(ctx, "aranha-" + key, dispW, dispH, dpi, surface, new RootDisplay.Callback() {
            public void done(int id, String err) {
                if (id >= 0) {
                    displayId = id;
                    log("display root confiavel " + id + " " + dispW + "x" + dispH + " dpi " + dpi + " (" + err + ")");
                    launch();
                } else {
                    log("display root falhou, usando o local: " + err);
                    try { surface.release(); } catch (Exception e) { }
                    startDisplayLocal();
                }
            }
        });
    }

    private void startDisplayLocal() {
        DisplayManager dmg = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
        surface = new Surface(st);
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
        vd = dmg.createVirtualDisplay("aranha-" + key, dispW, dispH, dpi, surface, flags);
        if (vd == null || vd.getDisplay() == null) {
            fail("createVirtualDisplay devolveu nulo");
            return;
        }
        displayId = vd.getDisplay().getDisplayId();
        log("display virtual " + displayId + " " + dispW + "x" + dispH + " dpi " + dpi + " sdk " + Build.VERSION.SDK_INT);
        launch();
    }

    private void launch() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    ensureHelper(ctx);
                    String out = RootShell.run((pkg != null && !pkg.isEmpty() ? "am force-stop " + pkg + "; " : "") + "am start --display " + displayId + " --windowingMode 1 " + amArgs + " 2>&1");
                    log("am start: " + out);
                    if (out != null && (out.contains("Error") || out.contains("Exception"))) {
                        fail("am start recusou");
                        return;
                    }
                    if (pkg != null && pkg.length() > 0) {
                        RootShell.send("( for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do sleep 0.2; T=$(dumpsys activity activities | grep -o 'Task{[0-9a-f]* #[0-9]* type=standard A=[0-9]*:" + pkg + "' | head -1 | sed 's/.*#\\([0-9]*\\) .*/\\1/'); [ -n \"$T\" ] && am display move-stack $T " + displayId + "; done ) >/dev/null 2>&1 &");
                    }
                    Thread.sleep(6000);
                    log("frames=" + frames);
                if (frames < 3) log("poucos frames, tela parada? " + frames);
                } catch (Throwable t) {
                    fail("launch: " + t);
                }
            }
        }).start();
    }

    /** Fecha a janela e, se der, cai no metodo antigo (janela freeform do sistema). */
    private void fail(final String why) {
        log("falhou: " + why);
        MAIN.post(new Runnable() {
            public void run() { close(false); }
        });
        if (fallback != null) new Thread(fallback).start();
    }

    private void close(boolean kill) {
        if (closed) return;
        closed = true;
        synchronized (WINDOWS) { WINDOWS.remove(key); }
        if (kill && pkg != null && pkg.length() > 0) {
            try { RootShell.send("am force-stop " + pkg); } catch (Exception e) { }
        }
        try { wm.removeView(root); } catch (Exception e) { }
        final VirtualDisplay d = vd;
        final Surface s = surface;
        MAIN.postDelayed(new Runnable() {
            public void run() {
                try { if (d != null) d.release(); } catch (Exception e) { }
                try { if (s != null) s.release(); } catch (Exception e) { }
            }
        }, kill ? 700 : 0);
    }

    // ---------- helper root (injeta os toques) ----------

    private static synchronized void ensureHelper(Context ctx) throws Exception {
        if (helper != null) {
            boolean alive = true;
            try { helper.exitValue(); alive = false; } catch (IllegalThreadStateException e) { }
            if (alive) return;
        }
        String cp = ctx.getPackageCodePath();
        helper = new ProcessBuilder("su", "-c",
                "CLASSPATH='" + cp + "' exec app_process /system/bin com.launchertop.aranha.InputServer").start();
        helperIn = helper.getOutputStream();
        final Process p = helper;
        new Thread(new Runnable() {
            public void run() {
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                    String l;
                    while ((l = r.readLine()) != null) log("helper: " + l);
                } catch (Exception e) { }
            }
        }).start();
    }

    private static void send(final String line) {
        IO.execute(new Runnable() {
            public void run() {
                try {
                    OutputStream o = helperIn;
                    if (o == null) return;
                    o.write((line + "\n").getBytes());
                    o.flush();
                } catch (Exception e) {
                    helperIn = null;
                }
            }
        });
    }

    /** Canto de redimensionar com as linhas diagonais. */
    private static class GripView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        GripView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            paint.setColor(0xDDFFFFFF);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2 * d);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            float m = 7 * d, gap = 6 * d;
            for (int i = 1; i <= 3; i++) {
                float o = m + i * gap;
                cv.drawLine(w - o, h - m, w - m, h - o, paint);
            }
        }
    }

    // ===== ponteiro agarra a janela (barra, setinha ou canto de redimensionar) =====
    private static long PG_SEQ = 0;
    private final long pgZ = ++PG_SEQ;
    private static VirtualWindow pgWin;
    private static boolean pgResize = false;
    private static int pgDx, pgDy, pgSx, pgSy, pgW0, pgH0;
    private static volatile int pgX, pgY;
    private static volatile boolean pgPending = false;

    public static boolean grabAt(int x, int y) {
        VirtualWindow best = null;
        boolean bestGrip = false;
        synchronized (WINDOWS) {
            for (VirtualWindow v : WINDOWS.values()) {
                if (v.wlp == null) continue;
                int l = v.wlp.x, t = v.wlp.y;
                int r = l + v.wlp.width, b = t + v.wlp.height;
                int g = v.dp(44);
                boolean inGrip = x >= r - g && x <= r && y >= b - g && y <= b;
                boolean inBar = x >= l && x <= r && y >= t && y <= t + v.barH;
                int aw = v.dp(22), ah = v.dp(60);
                int ay = t + v.wlp.height / 2 - ah / 2;
                boolean inArrow = x >= l && x <= l + aw && y >= ay && y <= ay + ah;
                if ((inGrip || inBar || inArrow) && (best == null || v.pgZ > best.pgZ)) {
                    best = v;
                    bestGrip = inGrip;
                }
            }
        }
        if (best == null) return false;
        pgWin = best;
        pgResize = bestGrip;
        pgDx = x - best.wlp.x;
        pgDy = y - best.wlp.y;
        pgSx = x; pgSy = y;
        pgW0 = best.wlp.width;
        pgH0 = best.wlp.height;
        return true;
    }

    public static void dragTo(int x, int y) {
        pgX = x; pgY = y;
        if (pgWin == null || pgPending) return;
        pgPending = true;
        MAIN.post(new Runnable() { public void run() {
            pgPending = false;
            VirtualWindow v = pgWin;
            if (v == null) return;
            if (pgResize) {
                v.wlp.width = Math.max(v.dp(200), pgW0 + (pgX - pgSx));
                v.wlp.height = Math.max(v.dp(200) + v.barH, pgH0 + (pgY - pgSy));
            } else {
                v.wlp.x = pgX - pgDx;
                v.wlp.y = Math.max(0, pgY - pgDy);
            }
            v.update();
        }});
    }

    public static void drop() {
        final VirtualWindow v = pgWin;
        final boolean rs = pgResize;
        pgWin = null;
        pgResize = false;
        if (v != null && rs) {
            MAIN.post(new Runnable() { public void run() { v.applySize(); } });
        }
    }
}
