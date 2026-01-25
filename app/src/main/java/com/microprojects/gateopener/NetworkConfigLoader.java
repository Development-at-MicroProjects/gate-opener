package com.microprojects.gateopener;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NetworkConfigLoader {

    private static final String TAG = "NetworkConfigLoader";
    private static final long RELOAD_INTERVAL_MINUTES = 5;
    
    private static NetworkConfigLoader instance;
    private Context context;
    private ScheduledExecutorService scheduler;
    private Handler mainHandler;

    private NetworkConfigLoader(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized NetworkConfigLoader getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkConfigLoader(context);
        }
        return instance;
    }

    public void startPeriodicReload() {
        stopPeriodicReload();
        
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                loadConfigFromNetwork();
            }
        }, 0, RELOAD_INTERVAL_MINUTES, TimeUnit.MINUTES);
        
        Log.d(TAG, "Started periodic config reload every " + RELOAD_INTERVAL_MINUTES + " minutes");
    }

    public void stopPeriodicReload() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    public void loadConfigFromNetwork() {
        SharedPreferences prefs = context.getSharedPreferences("GateOpenerPrefs", Context.MODE_PRIVATE);
        String configUrl = prefs.getString("config_url", "");
        
        if (configUrl.isEmpty()) {
            Log.d(TAG, "No config URL set, skipping network load");
            return;
        }
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String jsonConfig = fetchConfigFromUrl(configUrl);
                    if (jsonConfig != null) {
                        parseAndApplyConfig(jsonConfig);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load config from network: " + e.getMessage());
                    ActivityLogger.log(context, "Config reload failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private String fetchConfigFromUrl(String configUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(configUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
                reader.close();
                Log.d(TAG, "Config fetched successfully");
                return response.toString();
            } else {
                Log.e(TAG, "HTTP error: " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching config: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private void parseAndApplyConfig(String jsonConfig) {
        try {
            JSONObject config = new JSONObject(jsonConfig);
            SharedPreferences prefs = context.getSharedPreferences("GateOpenerPrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            boolean changed = false;

            if (config.has("shelly_url")) {
                String shellyUrl = config.getString("shelly_url");
                String currentUrl = prefs.getString("shelly_url", "");
                if (!shellyUrl.equals(currentUrl)) {
                    editor.putString("shelly_url", shellyUrl);
                    changed = true;
                    Log.d(TAG, "Updated Shelly URL: " + shellyUrl);
                }
            }

            if (config.has("shelly_method")) {
                String shellyMethod = config.getString("shelly_method");
                editor.putString("shelly_method", shellyMethod);
            }

            if (config.has("shelly_endpoint")) {
                String shellyEndpoint = config.getString("shelly_endpoint");
                editor.putString("shelly_endpoint", shellyEndpoint);
            }

            if (config.has("shelly_payload")) {
                String shellyPayload = config.getString("shelly_payload");
                editor.putString("shelly_payload", shellyPayload);
            }

            if (config.has("whitelist")) {
                JSONArray whitelistArray = config.getJSONArray("whitelist");
                StringBuilder whitelist = new StringBuilder();
                for (int i = 0; i < whitelistArray.length(); i++) {
                    if (i > 0) whitelist.append("\n");
                    whitelist.append(whitelistArray.getString(i));
                }
                String newWhitelist = whitelist.toString();
                String currentWhitelist = prefs.getString("whitelist", "");
                if (!newWhitelist.equals(currentWhitelist)) {
                    editor.putString("whitelist", newWhitelist);
                    changed = true;
                    Log.d(TAG, "Updated whitelist with " + whitelistArray.length() + " numbers");
                }
            }

            if (config.has("reload_interval_minutes")) {
                int interval = config.getInt("reload_interval_minutes");
                editor.putInt("reload_interval", interval);
            }

            editor.apply();

            if (changed) {
                WhitelistManager.getInstance(context).reloadWhitelist();
                ActivityLogger.log(context, "Config reloaded from network");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing config JSON: " + e.getMessage());
            ActivityLogger.log(context, "Config parse error: " + e.getMessage());
        }
    }

    public static String getSampleConfigJson() {
        return "{\n" +
               "  \"shelly_url\": \"http://192.168.1.100\",\n" +
               "  \"shelly_method\": \"POST\",\n" +
               "  \"shelly_endpoint\": \"/rpc/Switch.Set\",\n" +
               "  \"shelly_payload\": \"{\\\"id\\\":0,\\\"on\\\":true}\",\n" +
               "  \"whitelist\": [\n" +
               "    \"+32471234567\",\n" +
               "    \"+32487654321\",\n" +
               "    \"0471234567\"\n" +
               "  ],\n" +
               "  \"reload_interval_minutes\": 5\n" +
               "}";
    }
}
