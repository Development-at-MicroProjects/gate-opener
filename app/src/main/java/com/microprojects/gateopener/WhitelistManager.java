package com.microprojects.gateopener;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

public class WhitelistManager {

    private static final String TAG = "WhitelistManager";
    private static WhitelistManager instance;
    
    private Context context;
    private Set<String> whitelist;

    private WhitelistManager(Context context) {
        this.context = context.getApplicationContext();
        this.whitelist = new HashSet<String>();
        reloadWhitelist();
    }

    public static synchronized WhitelistManager getInstance(Context context) {
        if (instance == null) {
            instance = new WhitelistManager(context);
        }
        return instance;
    }

    public void reloadWhitelist() {
        whitelist.clear();
        
        SharedPreferences prefs = context.getSharedPreferences("GateOpenerPrefs", Context.MODE_PRIVATE);
        String whitelistData = prefs.getString("whitelist", "");
        
        if (whitelistData.isEmpty()) {
            Log.d(TAG, "Whitelist is empty");
            return;
        }
        
        String[] numbers = whitelistData.split("\n");
        for (String number : numbers) {
            String normalized = normalizeNumber(number.trim());
            if (!normalized.isEmpty()) {
                whitelist.add(normalized);
                Log.d(TAG, "Added to whitelist: " + normalized);
            }
        }
        
        Log.d(TAG, "Whitelist loaded with " + whitelist.size() + " numbers");
    }

    public boolean isWhitelisted(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }
        
        String normalized = normalizeNumber(phoneNumber);
        
        if (whitelist.contains(normalized)) {
            return true;
        }
        
        for (String whitelistedNumber : whitelist) {
            if (numbersMatch(normalized, whitelistedNumber)) {
                return true;
            }
        }
        
        return false;
    }

    private String normalizeNumber(String number) {
        if (number == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (char c : number.toCharArray()) {
            if (Character.isDigit(c) || c == '+') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private boolean numbersMatch(String incoming, String whitelisted) {
        String incomingDigits = incoming.replaceAll("[^0-9]", "");
        String whitelistedDigits = whitelisted.replaceAll("[^0-9]", "");
        
        if (incomingDigits.equals(whitelistedDigits)) {
            return true;
        }
        
        if (incomingDigits.length() >= 9 && whitelistedDigits.length() >= 9) {
            String incomingSuffix = incomingDigits.substring(Math.max(0, incomingDigits.length() - 9));
            String whitelistedSuffix = whitelistedDigits.substring(Math.max(0, whitelistedDigits.length() - 9));
            
            if (incomingSuffix.equals(whitelistedSuffix)) {
                return true;
            }
        }
        
        return false;
    }

    public int getWhitelistSize() {
        return whitelist.size();
    }
}
