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
            
            if (incomingNumber == null || incomingNumber.isEmpty()) {
                ActivityLogger.log(context, "UNKNOWN - REJECTED");
                rejectCall(context);
                return;
            }
            
            boolean isWhitelisted = WhitelistManager.getInstance(context).isWhitelisted(incomingNumber);
            
            if (isWhitelisted) {
                rejectCall(context);
                triggerGateInBackground(context, incomingNumber);
            } else {
                ActivityLogger.log(context, incomingNumber + " - UNKNOWN - REJECTED");
                rejectCall(context);
            }
        }
    }

    private static final int GATE_TRIGGER_ROUNDS = 3;
    private static final long ROUND_DELAY_MS = 3000;

    private void triggerGateInBackground(final Context context, final String phoneNumber) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String shellyUrl = context.getSharedPreferences("GateOpenerPrefs", Context.MODE_PRIVATE)
                        .getString("shelly_url", "");
                
                if (shellyUrl.isEmpty()) {
                    ActivityLogger.log(context, phoneNumber + " - WHITELISTED - FAILURE (Shelly URL not configured)");
                    return;
                }
                
                StringBuilder errorDetails = new StringBuilder();
                boolean success = false;
                
                for (int round = 1; round <= GATE_TRIGGER_ROUNDS && !success; round++) {
                    if (round > 1) {
                        // Try to wake up WiFi before retry
                        reassociateWifi(context);
                        try {
                            Thread.sleep(ROUND_DELAY_MS);
                        } catch (InterruptedException e) {
                            break;
                        }
                        errorDetails.append(" [Round ").append(round).append("] ");
                    }
                    success = ShellyClient.triggerGate(shellyUrl, context, errorDetails);
                }
                
                if (success) {
                    ActivityLogger.log(context, phoneNumber + " - WHITELISTED - SUCCESS");
                } else {
                    String wifiInfo = getWifiInfo(context);
                    ActivityLogger.log(context, phoneNumber + " - WHITELISTED - FAILURE | " + errorDetails.toString() + " | " + wifiInfo);
                }
            }
        }).start();
    }

    private void reassociateWifi(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiManager.reassociate();
            }
        } catch (Exception e) {
            // Ignore - best effort
        }
    }

    private String getWifiInfo(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo info = wifiManager.getConnectionInfo();
                if (info != null) {
                    int rssi = info.getRssi();
                    String ssid = info.getSSID();
                    int linkSpeed = info.getLinkSpeed();
                    return String.format("WiFi: %s, RSSI: %ddBm, Speed: %dMbps", ssid, rssi, linkSpeed);
                }
            }
        } catch (Exception e) {
            return "WiFi: check failed - " + e.getMessage();
        }
        return "WiFi: unavailable";
    }

    private void rejectCall(Context context) {
        CallRejector.rejectCall(context);
    }
}
