package com.microprojects.gateopener;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;

public class GateOpenerService extends Service {

    private static final int NOTIFICATION_ID = 1;
    private static boolean isRunning = false;
    
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    
    private Handler heartbeatHandler;
    private static final long KEEPALIVE_INTERVAL_MS = 30000; // Every 30 seconds
    private static final int LOG_FAILURE_EVERY_N = 10; // Log once per ~5 minutes of failures
    private int consecutiveFailures = 0;

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        acquireWakeLock();
        acquireWifiLock();
        startHeartbeat();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        
        startForegroundNotification();
        
        WhitelistManager.getInstance(this).reloadWhitelist();
        
        LocalConfigLoader.getInstance(this).startWatching();
        NetworkConfigLoader.getInstance(this).startPeriodicReload();
        
        ActivityLogger.log(this, "Service started");
        
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        LocalConfigLoader.getInstance(this).stopWatching();
        NetworkConfigLoader.getInstance(this).stopPeriodicReload();
        stopHeartbeat();
        releaseWifiLock();
        releaseWakeLock();
        ActivityLogger.log(this, "Service stopped");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "GateOpener::WakeLock"
            );
            wakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    private void acquireWifiLock() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "GateOpener::WifiLock");
            wifiLock.acquire();
        }
    }

    private void releaseWifiLock() {
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
            wifiLock = null;
        }
    }

    private void startHeartbeat() {
        heartbeatHandler = new Handler();
        heartbeatHandler.postDelayed(heartbeatRunnable, KEEPALIVE_INTERVAL_MS);
    }

    private void stopHeartbeat() {
        if (heartbeatHandler != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
            heartbeatHandler = null;
        }
    }

    private Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            final Context ctx = GateOpenerService.this;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String shellyUrl = getSharedPreferences("GateOpenerPrefs", MODE_PRIVATE)
                            .getString("shelly_url", "");
                    if (!shellyUrl.isEmpty()) {
                        boolean reachable = ShellyClient.ping(shellyUrl);
                        if (reachable) {
                            if (consecutiveFailures > 0) {
                                ActivityLogger.log(ctx, "KEEPALIVE: WiFi recovered after " + consecutiveFailures + " failures");
                            }
                            consecutiveFailures = 0;
                        } else {
                            consecutiveFailures++;
                            if (consecutiveFailures % LOG_FAILURE_EVERY_N == 1) {
                                ActivityLogger.log(ctx, "KEEPALIVE: Shelly unreachable (" + consecutiveFailures + ")");
                            }
                        }
                    }
                }
            }).start();
            if (heartbeatHandler != null) {
                heartbeatHandler.postDelayed(this, KEEPALIVE_INTERVAL_MS);
            }
        }
    };
}
