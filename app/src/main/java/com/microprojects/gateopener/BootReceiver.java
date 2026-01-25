package com.microprojects.gateopener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            
            Log.d(TAG, "Boot completed - starting GateOpener service");
            
            Intent serviceIntent = new Intent(context, GateOpenerService.class);
            context.startService(serviceIntent);
            
            ActivityLogger.log(context, "Device booted - Service auto-started");
        }
    }
}
