package com.microprojects.gateopener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
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
            
            // Log Wi-Fi state for diagnostics
            logWifiState(context);
            
            if (incomingNumber == null || incomingNumber.isEmpty()) {
                ActivityLogger.log(context, "Incoming call: Unknown number");
                rejectCall(context);
                return;
            }
            
            ActivityLogger.log(context, "Incoming call from: " + incomingNumber);
            
            boolean isWhitelisted = WhitelistManager.getInstance(context).isWhitelisted(incomingNumber);
            
            if (isWhitelisted) {
                ActivityLogger.log(context, "Number WHITELISTED - Rejecting call & triggering gate!");
                rejectCall(context);  // Reject immediately to send busy signal
                triggerGateInBackground(context);  // Then trigger gate
            } else {
                ActivityLogger.log(context, "Number NOT whitelisted - Rejecting");
                rejectCall(context);
            }
        }
    }

    private void triggerGateInBackground(final Context context) {
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

    private void logWifiState(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo info = wifiManager.getConnectionInfo();
                if (info != null) {
                    int rssi = info.getRssi();
                    String ssid = info.getSSID();
                    int linkSpeed = info.getLinkSpeed();
                    ActivityLogger.log(context, String.format(
                            "WiFi: %s, RSSI: %ddBm, Speed: %dMbps",
                            ssid, rssi, linkSpeed));
                }
            }
        } catch (Exception e) {
            ActivityLogger.log(context, "WiFi state check failed: " + e.getMessage());
        }
    }

    private void rejectCall(Context context) {
        CallRejector.rejectCall(context);
    }
}
