package com.microprojects.gateopener;

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

        boolean success = triggerShellyPro1(shellyBaseUrl);
        
        if (!success) {
            success = triggerShellyGen1(shellyBaseUrl);
        }
        
        return success;
    }

    private static boolean triggerShellyPro1(String baseUrl) {
        String endpoint = baseUrl + "/rpc/Switch.Set";
        
        try {
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            String jsonPayload = "{\"id\":0,\"on\":true}";
            
            OutputStream os = connection.getOutputStream();
            os.write(jsonPayload.getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Shelly Pro 1 response code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                Log.d(TAG, "Shelly Pro 1 response: " + response.toString());
                
                connection.disconnect();
                return true;
            }

            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "Shelly Pro 1 request failed: " + e.getMessage());
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
