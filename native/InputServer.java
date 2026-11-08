package com.launchertop.aranha;

import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/**
 * Roda como root via app_process (iniciado pelo VirtualWindow).
 * Le linhas do stdin e injeta toques direto no display virtual, sem abrir processo por evento.
 * Formato: E <action> <displayId> <downTime> <eventTime> <n> (<id> <x> <y>) x n
 */
public class InputServer {
    private static Object im;
    private static Method inject2, inject3, setDisplayId;

    public static void main(String[] args) {
        try {
            init();
            System.err.println("InputServer pronto");
            BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = r.readLine()) != null) {
                try {
                    handle(line);
                } catch (Throwable t) {
                    System.err.println("erro evento: " + t);
                }
            }
        } catch (Throwable t) {
            System.err.println("InputServer falhou: " + t);
        }
    }

    private static void init() throws Exception {
        Object inst = null;
        try {
            Class<?> g = Class.forName("android.hardware.input.InputManagerGlobal");
            inst = g.getMethod("getInstance").invoke(null);
        } catch (Throwable t) {
            inst = null;
        }
        if (inst == null) {
            Class<?> c = Class.forName("android.hardware.input.InputManager");
            inst = c.getMethod("getInstance").invoke(null);
        }
        im = inst;
        try {
            inject2 = im.getClass().getMethod("injectInputEvent", InputEvent.class, int.class);
        } catch (Throwable t) {
            inject2 = null;
        }
        if (inject2 == null) {
            inject3 = im.getClass().getMethod("injectInputEvent", InputEvent.class, int.class, int.class);
        }
        setDisplayId = InputEvent.class.getMethod("setDisplayId", int.class);
    }

    private static void handle(String line) throws Exception {
        String[] p = line.split(" ");
        if (p.length < 6 || !p[0].equals("E")) return;
        int action = Integer.parseInt(p[1]);
        int display = Integer.parseInt(p[2]);
        long down = Long.parseLong(p[3]);
        long when = Long.parseLong(p[4]);
        int n = Integer.parseInt(p[5]);
        MotionEvent.PointerProperties[] pp = new MotionEvent.PointerProperties[n];
        MotionEvent.PointerCoords[] pc = new MotionEvent.PointerCoords[n];
        for (int i = 0; i < n; i++) {
            pp[i] = new MotionEvent.PointerProperties();
            pp[i].id = Integer.parseInt(p[6 + i * 3]);
            pp[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            pc[i] = new MotionEvent.PointerCoords();
            pc[i].x = Float.parseFloat(p[7 + i * 3]);
            pc[i].y = Float.parseFloat(p[8 + i * 3]);
            pc[i].pressure = 1f;
            pc[i].size = 1f;
        }
        MotionEvent ev = MotionEvent.obtain(down, when, action, n, pp, pc, 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0);
        setDisplayId.invoke(ev, display);
        if (inject2 != null) {
            inject2.invoke(im, ev, 0);
        } else {
            inject3.invoke(im, ev, 0, -1);
        }
        ev.recycle();
    }
}
