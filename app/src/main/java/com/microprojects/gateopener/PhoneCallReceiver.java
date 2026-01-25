package com.microprojects.gateopener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

public class PhoneCallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
            return;
        }

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        
        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            
            if (incomingNumber == null || incomingNumber.isEmpty()) {
                ActivityLogger.log(context, "Incoming call: Unknown number");
                rejectCall(context);
                return;
            }
            
            ActivityLogger.log(context, "Incoming call from: " + incomingNumber);
            
            boolean isWhitelisted = WhitelistManager.getInstance(context).isWhitelisted(incomingNumber);
            
            if (isWhitelisted) {
                ActivityLogger.log(context, "Number WHITELISTED - Triggering gate!");
                triggerGate(context);
            } else {
                ActivityLogger.log(context, "Number NOT whitelisted - Rejecting");
            }
            
            rejectCall(context);
        }
    }

    private void triggerGate(final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String shellyUrl = context.getSharedPreferences("GateOpenerPrefs", Context.MODE_PRIVATE)
                        .getString("shelly_url", "");
                
                if (shellyUrl.isEmpty()) {
                    ActivityLogger.log(context, "ERROR: Shelly URL not configured");
                    return;
                }
                
                boolean success = ShellyClient.triggerGate(shellyUrl, context);
                
                if (success) {
                    ActivityLogger.log(context, "Gate triggered successfully!");
                } else {
                    ActivityLogger.log(context, "ERROR: Failed to trigger gate");
                }
            }
        }).start();
    }

    private void rejectCall(Context context) {
        CallRejector.rejectCall(context);
    }
}
