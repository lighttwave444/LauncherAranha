package com.launchertop.aranha;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.system.Os;
import android.util.Log;
import android.view.Surface;

import com.topjohnwu.superuser.ipc.RootService;

import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;

public class DisplayRootService extends RootService {
    static final String TAG = "ArHelper";
    static final String PKG = "com.android.shell";
    static final int TX_CREATE = 1;
    static final int TX_RELEASE = 2;

    private final ConcurrentHashMap<Integer, VirtualDisplay> displays = new ConcurrentHashMap<Integer, VirtualDisplay>();
    private boolean shell = false;

    @Override
    public IBinder onBind(Intent intent) {
        return new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                try {
                    if (code == TX_CREATE) {
                        String name = data.readString();
                        int w = data.readInt();
                        int h = data.readInt();
                        int dpi = data.readInt();
                        Surface s = Surface.CREATOR.createFromParcel(data);
                        try {
                            int id = create(name, w, h, dpi, s);
                            reply.writeInt(id);
                            reply.writeString("ok uid=" + android.os.Process.myUid());
                        } catch (Throwable t) {
                            Log.e(TAG, "create falhou", t);
                            reply.writeInt(-1);
                            reply.writeString(String.valueOf(t));
                        }
                        return true;
                    }
                    if (code == TX_RELEASE) {
                        int id = data.readInt();
                        VirtualDisplay vd = displays.remove(id);
                        if (vd != null) vd.release();
                        reply.writeInt(0);
                        reply.writeString("ok");
                        return true;
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "onTransact", t);
                }
                return false;
            }
        };
    }

    private int create(String name, int w, int h, int dpi, Surface s) throws Exception {
        try {
            return make(name, w, h, dpi, s);
        } catch (Throwable first) {
            Log.w(TAG, "falhou como uid " + android.os.Process.myUid() + ": " + first);
            if (shell || android.os.Process.myUid() != 0) throw new Exception(first);
            Os.setgid(2000);
            Os.setuid(2000);
            shell = true;
            Log.w(TAG, "agora uid=" + android.os.Process.myUid());
            return make(name, w, h, dpi, s);
        }
    }

    private int make(String name, int w, int h, int dpi, Surface s) throws Exception {
        Context fake = new ContextWrapper(this) {
            @Override
            public String getPackageName() { return PKG; }
            public String getOpPackageName() { return PKG; }
            @Override
            public Context getApplicationContext() { return this; }
        };
        Constructor<DisplayManager> c = DisplayManager.class.getDeclaredConstructor(Context.class);
        c.setAccessible(true);
        DisplayManager dm = c.newInstance(fake);
        // PUBLIC | OWN_CONTENT_ONLY | SUPPORTS_TOUCH | DESTROY_CONTENT_ON_REMOVAL | TRUSTED
        int flags = 1 | 8 | (1 << 6) | (1 << 8) | (1 << 10);
        VirtualDisplay vd = dm.createVirtualDisplay(name, w, h, dpi, s, flags);
        if (vd == null || vd.getDisplay() == null) throw new Exception("createVirtualDisplay nulo");
        int id = vd.getDisplay().getDisplayId();
        displays.put(id, vd);
        Log.i(TAG, "display criado id=" + id + " " + w + "x" + h + " dpi " + dpi + " uid=" + android.os.Process.myUid());
        return id;
    }
}
