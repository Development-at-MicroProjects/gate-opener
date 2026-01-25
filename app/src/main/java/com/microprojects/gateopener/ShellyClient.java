package com.microprojects.gateopener;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ShellyClient {

    private static final String TAG = "ShellyClient";
    private static final int TIMEOUT_MS = 5000;

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
                return triggerCustomShelly(shellyBaseUrl, customEndpoint, customMethod, customPayload);
            }
        }

        boolean success = triggerShellyPro1(shellyBaseUrl);
        
        if (!success) {
            success = triggerShellyGen1(shellyBaseUrl);
        }
        
        return success;
    }

    private static boolean triggerCustomShelly(String baseUrl, String endpoint, String method, String payload) {
        try {
            // Turn ON
            boolean onSuccess = sendCustomShellyCommand(baseUrl, endpoint, method, payload);
            if (!onSuccess) {
                return false;
            }
            
            Log.d(TAG, "Custom Shelly: ON sent, waiting 500ms before OFF");
            
            // Wait 500ms (pulse duration)
            Thread.sleep(500);
            
            // Turn OFF - replace "true" with "false" in payload
            String offPayload = payload.replace("true", "false");
            boolean offSuccess = sendCustomShellyCommand(baseUrl, endpoint, method, offPayload);
            Log.d(TAG, "Custom Shelly: OFF sent, success=" + offSuccess);
            
            return true; // Gate was triggered (ON was successful)
            
        } catch (Exception e) {
            Log.e(TAG, "Custom Shelly request failed: " + e.getMessage());
        }
        
        return false;
    }

    private static boolean sendCustomShellyCommand(String baseUrl, String endpoint, String method, String payload) {
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
            
            return responseCode == HttpURLConnection.HTTP_OK;
            
        } catch (Exception e) {
            Log.e(TAG, "Custom Shelly command failed: " + e.getMessage());
        }
        
        return false;
    }

    private static boolean triggerShellyPro1(String baseUrl) {
        String endpoint = baseUrl + "/rpc/Switch.Set";
        
        try {
            // Turn ON
            boolean onSuccess = sendShellyPro1Command(endpoint, true);
            if (!onSuccess) {
                return false;
            }
            
            Log.d(TAG, "Shelly Pro 1: ON sent, waiting 500ms before OFF");
            
            // Wait 500ms (pulse duration)
            Thread.sleep(500);
            
            // Turn OFF
            boolean offSuccess = sendShellyPro1Command(endpoint, false);
            Log.d(TAG, "Shelly Pro 1: OFF sent, success=" + offSuccess);
            
            return true; // Gate was triggered (ON was successful)
            
        } catch (Exception e) {
            Log.e(TAG, "Shelly Pro 1 request failed: " + e.getMessage());
        }
        
        return false;
    }

    private static boolean sendShellyPro1Command(String endpoint, boolean on) {
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
                return true;
            }

            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "Shelly Pro 1 command failed (on=" + on + "): " + e.getMessage());
        }
        
        return false;
    }

    private static boolean triggerShellyGen1(String baseUrl) {
        String endpoint = baseUrl + "/relay/0?turn=on";
        
        try {
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Shelly Gen1 response code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                Log.d(TAG, "Shelly Gen1 response: " + response.toString());
                
                connection.disconnect();
                return true;
            }

            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "Shelly Gen1 request failed: " + e.getMessage());
        }
        
        return false;
    }
}
