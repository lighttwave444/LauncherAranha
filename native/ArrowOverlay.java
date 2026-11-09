package com.launchertop.aranha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Setas laterais como overlay proprio: ficam sempre acima das janelas. */
public class ArrowOverlay {
    public interface Tap { void onTap(String id); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Arrow> ARROWS = new LinkedHashMap<String, Arrow>();
    private static WindowManager wm;
    private static boolean shown = false;
    private static Tap tap;

    private static class Arrow extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        WindowManager.LayoutParams lp;
        boolean on = true, attached = false;
        Arrow(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xAA5A6472);
            c.drawRoundRect(new RectF(-w, 0, w, h), w * 0.75f, w * 0.75f, p);
            float s = w * 0.28f, cx = w / 2f, cy = h / 2f;
            float d = on ? 1f : -1f;
            path.reset();
            path.moveTo(cx + d * s / 2f, cy - s);
            path.lineTo(cx - d * s / 2f, cy);
            path.lineTo(cx + d * s / 2f, cy + s);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(w * 0.14f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(0xFFFFFFFF);
            c.drawPath(path, p);
        }
    }

    private static int type() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    public static void sync(final Context ctx, final JSONArray items,
                            final boolean visible, final Tap t) {
        MAIN.post(new Runnable() { public void run() {
            try {
                tap = t;
                if (wm == null) wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                if (!visible) { hideAll(); return; }
                shown = true;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject o = items.getJSONObject(i);
                    final String id = o.getString("id");
                    Arrow a = ARROWS.get(id);
                    if (a == null) {
                        a = new Arrow(ctx);
                        a.lp = new WindowManager.LayoutParams(1, 1, type(),
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                                PixelFormat.TRANSLUCENT);
                        a.lp.gravity = Gravity.TOP | Gravity.LEFT;
                        a.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                if (tap != null) tap.onTap(id);
                            }
                        });
                        ARROWS.put(id, a);
                    }
                    a.on = o.optBoolean("on", true);
                    a.lp.x = 0;
                    a.lp.y = o.getInt("y");
                    a.lp.width = o.getInt("w");
                    a.lp.height = o.getInt("h");
                    if (a.attached) wm.updateViewLayout(a, a.lp);
                    else { wm.addView(a, a.lp); a.attached = true; }
                    a.invalidate();
                }
            } catch (Exception e) { }
        }});
    }

    private static void hideAll() {
        shown = false;
        for (Arrow a : ARROWS.values()) {
            if (a.attached) {
                try { wm.removeViewImmediate(a); } catch (Exception e) { }
                a.attached = false;
            }
        }
    }

    private static final Runnable FRONT = new Runnable() { public void run() {
        if (wm == null || !shown) return;
        for (Arrow a : ARROWS.values()) {
            try {
                if (a.attached) { a.attached = false; wm.removeViewImmediate(a); }
                wm.addView(a, a.lp);
                a.attached = true;
            } catch (Exception e) { a.attached = false; }
        }
    }};

    /** Chamar depois de qualquer addView de janela: traz as setas pra frente. */
    public static void front() {
        MAIN.removeCallbacks(FRONT);
        MAIN.postDelayed(FRONT, 80);
    }
}
