package com.microprojects.gateopener;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ShellyClient {

    private static final String TAG = "ShellyClient";
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 500;
    private static final int VERIFY_DELAY_MS = 100;

    public static boolean triggerGate(String shellyBaseUrl) {
        return triggerGate(shellyBaseUrl, null);
    }

    public static boolean triggerGate(String shellyBaseUrl, Context context) {
        if (shellyBaseUrl == null || shellyBaseUrl.isEmpty()) {
            Log.e(TAG, "Shelly URL is empty");
            return false;
        }

        shellyBaseUrl = shellyBaseUrl.trim();
        if (!shellyBaseUrl.startsWith("http://") && !shellyBaseUrl.startsWith("https://")) {
            shellyBaseUrl = "http://" + shellyBaseUrl;
        }
        
        if (shellyBaseUrl.endsWith("/")) {
            shellyBaseUrl = shellyBaseUrl.substring(0, shellyBaseUrl.length() - 1);
        }

        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences("GateOpenerPrefs", Context.MODE_PRIVATE);
            String customEndpoint = prefs.getString("shelly_endpoint", "");
            String customMethod = prefs.getString("shelly_method", "");
            String customPayload = prefs.getString("shelly_payload", "");
            
            if (!customEndpoint.isEmpty()) {
                return triggerCustomShelly(shellyBaseUrl, customEndpoint, customMethod, customPayload, context);
            }
        }

        boolean success = triggerShellyPro1(shellyBaseUrl, context);
        
        if (!success) {
            success = triggerShellyGen1(shellyBaseUrl, context);
        }
        
        return success;
    }

    private static void logToUI(Context context, String message) {
        Log.d(TAG, message);
        if (context != null) {
            ActivityLogger.log(context, message);
        }
    }

    private static boolean triggerCustomShelly(String baseUrl, String endpoint, String method, String payload, Context context) {
        // Turn ON with retry
        boolean onSuccess = sendCustomShellyCommandWithRetry(baseUrl, endpoint, method, payload, context);
        if (!onSuccess) {
            logToUI(context, "Custom Shelly: Failed to turn ON after " + MAX_RETRIES + " attempts");
            return false;
        }
        
        // Success - no logging needed
        
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Log.e(TAG, "Sleep interrupted: " + e.getMessage());
        }
        
        // Turn OFF - replace "true" with "false" in payload
        String offPayload = payload.replace("true", "false");
        boolean offSuccess = sendCustomShellyCommandWithRetry(baseUrl, endpoint, method, offPayload, context);
        if (!offSuccess) {
            logToUI(context, "Warning: Relay OFF failed");
        }
        
        return true; // Gate was triggered (ON was successful)
    }

    private static boolean sendCustomShellyCommandWithRetry(String baseUrl, String endpoint, String method, String payload, Context context) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 1) {
                logToUI(context, "Retry attempt " + attempt + "/" + MAX_RETRIES);
            }
            
            boolean success = sendCustomShellyCommand(baseUrl, endpoint, method, payload, context);
            if (success) {
                return true;
            }
            
            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                } catch (InterruptedException e) {
                    Log.e(TAG, "Retry delay interrupted");
                }
            }
        }
        return false;
    }

    private static boolean sendCustomShellyCommand(String baseUrl, String endpoint, String method, String payload, Context context) {
        String fullUrl = baseUrl + endpoint;
        
        try {
            URL url = new URL(fullUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method.isEmpty() ? "POST" : method.toUpperCase());
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            if (!payload.isEmpty() && ("POST".equalsIgnoreCase(method) || method.isEmpty())) {
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                OutputStream os = connection.getOutputStream();
                os.write(payload.getBytes("UTF-8"));
                os.flush();
                os.close();
            }

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Custom Shelly response code: " + responseCode + " payload: " + payload);
            connection.disconnect();
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                logToUI(context, "HTTP Error: " + responseCode);
                return false;
            }
            return true;
            
        } catch (Exception e) {
            logToUI(context, "Connection error: " + e.getMessage());
            Log.e(TAG, "Custom Shelly command failed: " + e.getMessage());
        }
        
        return false;
    }

    private static boolean triggerShellyPro1(String baseUrl, Context context) {
        String setEndpoint = baseUrl + "/rpc/Switch.Set";
        String statusEndpoint = baseUrl + "/rpc/Switch.GetStatus?id=0";
        
        // Turn ON with retry and verification
        boolean onSuccess = sendShellyPro1CommandWithRetry(setEndpoint, true, statusEndpoint, context);
        if (!onSuccess) {
            logToUI(context, "Shelly Pro 1: Failed to turn ON after " + MAX_RETRIES + " attempts");
            return false;
        }
        
        // Success - no logging needed
        
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Log.e(TAG, "Sleep interrupted: " + e.getMessage());
        }
        
        // Turn OFF with retry
        boolean offSuccess = sendShellyPro1CommandWithRetry(setEndpoint, false, statusEndpoint, context);
        if (!offSuccess) {
            logToUI(context, "Warning: Relay OFF failed");
        }
        
        return true; // Gate was triggered (ON was successful and verified)
    }

    private static boolean sendShellyPro1CommandWithRetry(String endpoint, boolean on, String statusEndpoint, Context context) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 1) {
                logToUI(context, "Retry attempt " + attempt + "/" + MAX_RETRIES + " (" + (on ? "ON" : "OFF") + ")");
            }
            
            boolean commandSent = sendShellyPro1Command(endpoint, on, context);
            if (!commandSent) {
                Log.w(TAG, "Command failed on attempt " + attempt);
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Retry delay interrupted");
                    }
                }
                continue;
            }
            
            // Verify the relay state
            try {
                Thread.sleep(VERIFY_DELAY_MS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Verify delay interrupted");
            }
            
            Boolean actualState = getRelayState(statusEndpoint, context);
            if (actualState == null) {
                logToUI(context, "Could not verify relay state");
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Retry delay interrupted");
                    }
                }
                continue;
            }
            
            if (actualState == on) {
                Log.d(TAG, "Relay state verified: " + (on ? "ON" : "OFF"));
                return true;
            } else {
                logToUI(context, "Relay state mismatch! Expected: " + on + ", Actual: " + actualState);
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Retry delay interrupted");
                    }
                }
            }
        }
        
        return false;
    }

    private static Boolean getRelayState(String statusEndpoint, Context context) {
        try {
            URL url = new URL(statusEndpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                connection.disconnect();
                
                String responseStr = response.toString();
                Log.d(TAG, "GetStatus response: " + responseStr);
                
                // Parse JSON response to get output state
                JSONObject json = new JSONObject(responseStr);
                if (json.has("output")) {
                    return json.getBoolean("output");
                }
            }
            
            connection.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get relay state: " + e.getMessage());
        }
        
        return null;
    }

    private static boolean sendShellyPro1Command(String endpoint, boolean on, Context context) {
        try {
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            String jsonPayload = "{\"id\":0,\"on\":" + on + "}";
            
            OutputStream os = connection.getOutputStream();
            os.write(jsonPayload.getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Shelly Pro 1 response code: " + responseCode + " (on=" + on + ")");

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                connection.disconnect();
                
                String responseStr = response.toString();
                Log.d(TAG, "Shelly Pro 1 response: " + responseStr);
                
                // Validate response - should contain "was_on" field
                if (responseStr.contains("was_on")) {
                    return true;
                } else {
                    logToUI(context, "Unexpected response: " + responseStr);
                    return false;
                }
            } else {
                // Read error stream for more info
                try {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine);
                    }
                    errorReader.close();
                    logToUI(context, "HTTP Error " + responseCode + ": " + errorResponse.toString());
                } catch (Exception ignored) {
                    logToUI(context, "HTTP Error: " + responseCode);
                }
            }

            connection.disconnect();
            
        } catch (Exception e) {
            logToUI(context, "Connection error: " + e.getMessage());
            Log.e(TAG, "Shelly Pro 1 command failed (on=" + on + "): " + e.getMessage());
        }
        
        return false;
    }

    private static boolean triggerShellyGen1(String baseUrl, Context context) {
        // Turn ON with retry
        boolean onSuccess = sendShellyGen1CommandWithRetry(baseUrl, true, context);
        if (!onSuccess) {
            logToUI(context, "Shelly Gen1: Failed to turn ON after " + MAX_RETRIES + " attempts");
            return false;
        }
        
        // Success - no logging needed
        
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Log.e(TAG, "Sleep interrupted: " + e.getMessage());
        }
        
        // Turn OFF with retry
        boolean offSuccess = sendShellyGen1CommandWithRetry(baseUrl, false, context);
        if (!offSuccess) {
            logToUI(context, "Warning: Relay OFF failed");
        }
        
        return true;
    }

    private static boolean sendShellyGen1CommandWithRetry(String baseUrl, boolean on, Context context) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 1) {
                logToUI(context, "Retry attempt " + attempt + "/" + MAX_RETRIES + " (" + (on ? "ON" : "OFF") + ")");
            }
            
            boolean success = sendShellyGen1Command(baseUrl, on, context);
            if (success) {
                return true;
            }
            
            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Retry delay interrupted");
                }
            }
        }
        return false;
    }

    private static boolean sendShellyGen1Command(String baseUrl, boolean on, Context context) {
        String endpoint = baseUrl + "/relay/0?turn=" + (on ? "on" : "off");
        
        try {
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Shelly Gen1 response code: " + responseCode + " (on=" + on + ")");

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                String responseStr = response.toString();
                Log.d(TAG, "Shelly Gen1 response: " + responseStr);
                
                // Validate response contains expected state
                if (responseStr.contains("\"ison\":" + on) || responseStr.contains("\"ison\": " + on)) {
                    Log.d(TAG, "Shelly Gen1 state verified: " + (on ? "ON" : "OFF"));
                }
                
                connection.disconnect();
                return true;
            } else {
                logToUI(context, "HTTP Error: " + responseCode);
            }

            connection.disconnect();
            
        } catch (Exception e) {
            logToUI(context, "Connection error: " + e.getMessage());
            Log.e(TAG, "Shelly Gen1 request failed (on=" + on + "): " + e.getMessage());
        }
        
        return false;
    }
}
