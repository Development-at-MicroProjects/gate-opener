package com.microprojects.gateopener;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ActivityLogger {

    private static final String TAG = "ActivityLogger";
    private static final int MAX_LOG_LINES = 50;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);

    public static void log(Context context, String message) {
        String timestamp = dateFormat.format(new Date());
        String logEntry = timestamp + " - " + message;
        
        Log.d(TAG, logEntry);
        
        SharedPreferences prefs = context.getSharedPreferences("GateOpenerPrefs", Context.MODE_PRIVATE);
        String existingLog = prefs.getString("activity_log", "");
        
        String newLog = logEntry + "\n" + existingLog;
        
        String[] lines = newLog.split("\n");
        if (lines.length > MAX_LOG_LINES) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < MAX_LOG_LINES; i++) {
                sb.append(lines[i]);
                if (i < MAX_LOG_LINES - 1) {
                    sb.append("\n");
                }
            }
            newLog = sb.toString();
        }
        
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("activity_log", newLog);
        editor.apply();
        
        Intent intent = new Intent("com.microprojects.gateopener.LOG_UPDATE");
        context.sendBroadcast(intent);
    }

    public static void clearLog(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("GateOpenerPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("activity_log", "Log cleared");
        editor.apply();
        
        Intent intent = new Intent("com.microprojects.gateopener.LOG_UPDATE");
        context.sendBroadcast(intent);
    }
}
