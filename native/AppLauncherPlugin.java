package com.launchertop.aranha;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "AppLauncher")
public class AppLauncherPlugin extends Plugin {

    // true  = abre os apps na janela propria do launcher (display virtual)
    // false = volta para a janela freeform do sistema (metodo antigo)
    private static final boolean USE_VIRTUAL = true;

    private boolean runRoot(String cmd) {
        try {
            return RootShell.ok(cmd);
        } catch (Exception e) {
            return false;
        }
    }

    /** Metodo antigo: janela freeform do sistema com a moldura por cima. */
    private boolean openFreeform(String pkg, String action) {
        PackageManager pm = getContext().getPackageManager();
        String cmd = null;
        if ("dial".equals(action)) {
            cmd = "am start --windowingMode 5 -a android.intent.action.DIAL";
        } else if (!"settings".equals(action)) {
            Intent li = pm.getLaunchIntentForPackage(pkg);
            if (li != null && li.getComponent() != null) {
                cmd = "am start --windowingMode 5 -n '" + li.getComponent().flattenToShortString() + "'";
            }
        }
        if (cmd != null && runRoot(cmd)) {
            if (pkg != null && !pkg.isEmpty() && !WindowFrames.has(pkg)) {
                WindowFrames.attach(getContext().getApplicationContext(), pkg);
            }
            return true;
        }
        return false;
    }

    @PluginMethod
    public void open(PluginCall call) {
        JSObject res = new JSObject();
        try {
            final String pkg = call.getString("pkg", "");
            final String action = call.getString("action", "");
            boolean win = Boolean.TRUE.equals(call.getBoolean("win", false));
            PackageManager pm = getContext().getPackageManager();

            if (win) {
                // 1) janela propria (display virtual)
                if (USE_VIRTUAL) {
                    String args = null;
                    if ("dial".equals(action)) {
                        args = "-a android.intent.action.DIAL";
                    } else if (!"settings".equals(action)) {
                        Intent li = pm.getLaunchIntentForPackage(pkg);
                        if (li != null && li.getComponent() != null) {
                            args = "-n '" + li.getComponent().flattenToShortString() + "'";
                        }
                    }
                    if (args != null) {
                        String key = (pkg == null || pkg.isEmpty()) ? action : pkg;
                        boolean started = VirtualWindow.has(key)
                                || VirtualWindow.open(getContext().getApplicationContext(), key, pkg, args,
                                new Runnable() {
                                    public void run() { openFreeform(pkg, action); }
                                });
                        if (started) {
                            res.put("ok", true);
                            res.put("win", true);
                            call.resolve(res);
                            return;
                        }
                    }
                }

                // 2) janela flutuante (freeform) via root
                if (openFreeform(pkg, action)) {
                    res.put("ok", true);
                    res.put("win", true);
                    call.resolve(res);
                    return;
                }
            }

            if (win) { res.put("ok", false); call.resolve(res); return; }
            // 3) abertura normal (tela cheia)
            Intent i;
            if ("dial".equals(action)) {
                i = new Intent(Intent.ACTION_DIAL);
            } else if ("settings".equals(action)) {
                i = new Intent(Settings.ACTION_SETTINGS);
            } else {
                i = pm.getLaunchIntentForPackage(pkg);
            }
            if (i == null) {
                res.put("ok", false);
                call.resolve(res);
                return;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            res.put("ok", true);
            call.resolve(res);
        } catch (Exception e) {
            res.put("ok", false);
            call.resolve(res);
        }
    }
}
