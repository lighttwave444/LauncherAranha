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

    private boolean runRoot(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @PluginMethod
    public void open(PluginCall call) {
        JSObject res = new JSObject();
        try {
            String pkg = call.getString("pkg", "");
            String action = call.getString("action", "");
            boolean win = Boolean.TRUE.equals(call.getBoolean("win", false));
            PackageManager pm = getContext().getPackageManager();

            // 1) em janela flutuante (freeform) via root
            if (win) {
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
                    res.put("ok", true);
                    res.put("win", true);
                    call.resolve(res);
                    return;
                }
            }

            // 2) abertura normal (tela cheia)
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
