package com.launchertop.aranha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/** Ponteiro do mouse como overlay: fica sempre acima das janelas. */
public class PointerOverlay {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WindowManager wm;
    private static PView view;
    private static WindowManager.LayoutParams lp;
    private static boolean attached = false;

    private static class PView extends View {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        PView(Context c) {
            super(c);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xFFFFFFFF);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(0xFF111111);
            stroke.setStrokeWidth(1.5f);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            path.moveTo(2f, 2f);
            path.lineTo(2f, 23f);
            path.lineTo(7.5f, 18f);
            path.lineTo(11.1f, 26f);
            path.lineTo(14.7f, 24.4f);
            path.lineTo(11.1f, 16.4f);
            path.lineTo(18.7f, 16.4f);
            path.close();
        }
        @Override protected void onDraw(Canvas c) {
            float s = getWidth() / 24f;
            c.save();
            c.scale(s, s);
            c.drawPath(path, fill);
            c.drawPath(path, stroke);
            c.restore();
        }
    }

    private static int type() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    public static void set(final Context ctx, final int x, final int y,
                           final int w, final int h, final boolean vis) {
        MAIN.post(new Runnable() { public void run() {
            try {
                if (wm == null) wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                if (!vis || w <= 0 || h <= 0) {
                    if (attached) { wm.removeViewImmediate(view); attached = false; }
                    return;
                }
                if (view == null) {
                    view = new PView(ctx);
                    lp = new WindowManager.LayoutParams(w, h, type(),
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            PixelFormat.TRANSLUCENT);
                    lp.gravity = Gravity.TOP | Gravity.LEFT;
                }
                lp.x = x; lp.y = y; lp.width = w; lp.height = h;
                if (attached) wm.updateViewLayout(view, lp);
                else { wm.addView(view, lp); attached = true; }
            } catch (Exception e) { }
        }});
    }

    private static final Runnable FRONT = new Runnable() { public void run() {
        if (wm == null || view == null || !attached) return;
        try { wm.removeViewImmediate(view); } catch (Exception e) { }
        try { wm.addView(view, lp); attached = true; } catch (Exception e) { attached = false; }
    }};

    /** Chamar depois de abrir qualquer janela: traz o ponteiro pra frente. */
    public static void front() {
        MAIN.removeCallbacks(FRONT);
        MAIN.postDelayed(FRONT, 80);
    }
}
