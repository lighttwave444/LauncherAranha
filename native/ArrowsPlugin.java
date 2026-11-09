package com.launchertop.aranha;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "Arrows")
public class ArrowsPlugin extends Plugin {
    @PluginMethod
    public void sync(final PluginCall call) {
        JSArray items = call.getArray("items");
        if (items == null) items = new JSArray();
        boolean visible = call.getBoolean("visible", true);
        ArrowOverlay.sync(getContext().getApplicationContext(), items, visible,
            new ArrowOverlay.Tap() {
                @Override public void onTap(String id) {
                    JSObject o = new JSObject();
                    o.put("id", id);
                    notifyListeners("arrowTap", o);
                }
            });
        call.resolve();
    }
}
