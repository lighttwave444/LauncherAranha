package com.launchertop.aranha;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.view.Surface;

import com.topjohnwu.superuser.ipc.RootService;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class RootDisplay {
    static final Handler MAIN = new Handler(Looper.getMainLooper());
    static IBinder service;
    static boolean binding = false;
    static final ArrayList<Runnable> pending = new ArrayList<Runnable>();

    public interface Callback { void done(int displayId, String err); }

    /** Cria uma tela virtual confiavel pelo servico root. O callback roda na thread principal. */
    public static void create(final Context ctx, final String name, final int w, final int h,
                              final int dpi, final Surface s, final Callback cb) {
        final AtomicBoolean finished = new AtomicBoolean(false);
        final Callback once = new Callback() {
            public void done(final int id, final String err) {
                if (!finished.compareAndSet(false, true)) return;
                MAIN.post(new Runnable() { public void run() { cb.done(id, err); } });
            }
        };
        MAIN.postDelayed(new Runnable() {
            public void run() { once.done(-1, "timeout falando com o servico root"); }
        }, 10000);

        final Runnable go = new Runnable() {
            public void run() {
                new Thread(new Runnable() {
                    public void run() {
                        Parcel d = Parcel.obtain();
                        Parcel r = Parcel.obtain();
                        try {
                            d.writeString(name);
                            d.writeInt(w);
                            d.writeInt(h);
                            d.writeInt(dpi);
                            s.writeToParcel(d, 0);
                            service.transact(1, d, r, 0);
                            int id = r.readInt();
                            String msg = r.readString();
                            once.done(id, msg);
                        } catch (Throwable t) {
                            once.done(-1, String.valueOf(t));
                        } finally {
                            d.recycle();
                            r.recycle();
                        }
                    }
                }).start();
            }
        };

        MAIN.post(new Runnable() {
            public void run() {
                if (service != null && service.isBinderAlive()) { go.run(); return; }
                pending.add(go);
                if (binding) return;
                binding = true;
                try {
                    RootService.bind(new Intent(ctx, DisplayRootService.class), new ServiceConnection() {
                        public void onServiceConnected(ComponentName n, IBinder b) {
                            service = b;
                            binding = false;
                            ArrayList<Runnable> l = new ArrayList<Runnable>(pending);
                            pending.clear();
                            for (Runnable x : l) x.run();
                        }
                        public void onServiceDisconnected(ComponentName n) {
                            service = null;
                            binding = false;
                        }
                    });
                } catch (Throwable t) {
                    binding = false;
                    pending.clear();
                    once.done(-1, "bind: " + t);
                }
            }
        });
    }

    public static void release(final int id) {
        new Thread(new Runnable() {
            public void run() {
                IBinder b = service;
                if (b == null) return;
                Parcel d = Parcel.obtain();
                Parcel r = Parcel.obtain();
                try {
                    d.writeInt(id);
                    b.transact(2, d, r, 0);
                } catch (Throwable t) {
                } finally {
                    d.recycle();
                    r.recycle();
                }
            }
        }).start();
    }
}
