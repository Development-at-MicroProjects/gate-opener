package com.microprojects.gateopener;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.FileObserver;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class LocalConfigLoader {

    private static final String TAG = "LocalConfigLoader";
    private static final String CONFIG_FILE = "config.json";
    
    private static LocalConfigLoader instance;
    private Context context;
    private FileObserver fileObserver;
    private File configFile;

    private LocalConfigLoader(Context context) {
        this.context = context.getApplicationContext();
        this.configFile = getConfigFile(this.context);
    }

    public static synchronized LocalConfigLoader getInstance(Context context) {
        if (instance == null) {
            instance = new LocalConfigLoader(context);
        }
        return instance;
    }

    public static File getConfigFile(Context context) {
        return new File(context.getFilesDir(), CONFIG_FILE);
    }

    public static File getConfigDir(Context context) {
        return context.getFilesDir();
    }

    public String getConfigPath() {
        return getConfigFile(context).getAbsolutePath();
    }

    public void startWatching() {
        stopWatching();
        
        File dir = getConfigDir(context);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                Log.d(TAG, "Created config directory: " + dir.getAbsolutePath());
            }
        }
        
        File file = getConfigFile(context);
        if (!file.exists()) {
            createSampleConfig();
        }

        loadConfigFromFile();

        fileObserver = new FileObserver(dir.getAbsolutePath(), FileObserver.MODIFY | FileObserver.CLOSE_WRITE) {
            @Override
            public void onEvent(int event, String path) {
                if (path != null && path.equals(CONFIG_FILE)) {
                    Log.d(TAG, "Config file changed, reloading...");
                    loadConfigFromFile();
                }
            }
        };
        fileObserver.startWatching();
        Log.d(TAG, "Started watching: " + dir.getAbsolutePath());
    }

    public void stopWatching() {
        if (fileObserver != null) {
            fileObserver.stopWatching();
            fileObserver = null;
        }
    }

    public void loadConfigFromFile() {
        File file = getConfigFile(context);
        
        if (!file.exists()) {
            Log.d(TAG, "Config file not found: " + file.getAbsolutePath());
            ActivityLogger.log(context, "No local config file found at " + file.getAbsolutePath());
            return;
        }
        
        try {
            StringBuilder content = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            
            parseAndApplyConfig(content.toString());
            Log.d(TAG, "Config loaded from: " + file.getAbsolutePath());
            
        } catch (Exception e) {
            Log.e(TAG, "Error reading config file: " + e.getMessage());
            ActivityLogger.log(context, "Config read error: " + e.getMessage());
        }
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

            editor.apply();

            if (changed) {
                WhitelistManager.getInstance(context).reloadWhitelist();
                ActivityLogger.log(context, "Config reloaded from local file");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing config JSON: " + e.getMessage());
            ActivityLogger.log(context, "Config parse error: " + e.getMessage());
        }
    }

    private void createSampleConfig() {
        File file = getConfigFile(context);
        try {
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write("{\n");
            writer.write("  \"shelly_url\": \"http://192.168.1.100\",\n");
            writer.write("  \"shelly_method\": \"POST\",\n");
            writer.write("  \"shelly_endpoint\": \"/rpc/Switch.Set\",\n");
            writer.write("  \"shelly_payload\": \"{\\\"id\\\":0,\\\"on\\\":true}\",\n");
            writer.write("  \"whitelist\": [\n");
            writer.write("    \"+32471234567\",\n");
            writer.write("    \"+32487654321\"\n");
            writer.write("  ]\n");
            writer.write("}\n");
            writer.close();
            Log.d(TAG, "Created sample config at: " + file.getAbsolutePath());
            ActivityLogger.log(context, "Created sample config at " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error creating sample config: " + e.getMessage());
        }
    }

    public static String getConfigPath(Context context) {
        return getConfigFile(context).getAbsolutePath();
    }
}
