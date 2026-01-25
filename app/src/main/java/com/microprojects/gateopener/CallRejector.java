package com.microprojects.gateopener;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.lang.reflect.Method;

public class CallRejector {

    private static final String TAG = "CallRejector";

    public static void rejectCall(Context context) {
        try {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            
            Class<?> telephonyClass = Class.forName(telephonyManager.getClass().getName());
            Method getITelephonyMethod = telephonyClass.getDeclaredMethod("getITelephony");
            getITelephonyMethod.setAccessible(true);
            
            Object telephonyInterface = getITelephonyMethod.invoke(telephonyManager);
            
            Class<?> telephonyInterfaceClass = Class.forName(telephonyInterface.getClass().getName());
            Method endCallMethod = telephonyInterfaceClass.getDeclaredMethod("endCall");
            endCallMethod.setAccessible(true);
            
            endCallMethod.invoke(telephonyInterface);
            
            Log.d(TAG, "Call rejected successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to reject call: " + e.getMessage());
            e.printStackTrace();
            
            tryAlternativeReject(context);
        }
    }

    private static void tryAlternativeReject(Context context) {
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String.class);
            
            Object telephonyBinder = getServiceMethod.invoke(null, Context.TELEPHONY_SERVICE);
            
            if (telephonyBinder != null) {
                Class<?> stubClass = Class.forName("com.android.internal.telephony.ITelephony$Stub");
                Method asInterfaceMethod = stubClass.getDeclaredMethod("asInterface", android.os.IBinder.class);
                
                Object telephonyInterface = asInterfaceMethod.invoke(null, telephonyBinder);
                
                Method endCallMethod = telephonyInterface.getClass().getDeclaredMethod("endCall");
                endCallMethod.invoke(telephonyInterface);
                
                Log.d(TAG, "Call rejected via alternative method");
            }
        } catch (Exception e) {
            Log.e(TAG, "Alternative reject also failed: " + e.getMessage());
        }
    }
}
