package com.launchertop.aranha;

import android.content.Intent;
import android.provider.Settings;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "AppLauncher")
public class AppLauncherPlugin extends Plugin {
    @PluginMethod
    public void open(PluginCall call) {
        JSObject res = new JSObject();
        try {
            String pkg = call.getString("pkg", "");
            String action = call.getString("action", "");
            Intent i;
            if ("dial".equals(action)) {
                i = new Intent(Intent.ACTION_DIAL);
            } else if ("settings".equals(action)) {
                i = new Intent(Settings.ACTION_SETTINGS);
            } else {
                i = getContext().getPackageManager().getLaunchIntentForPackage(pkg);
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
