package com.microprojects.gateopener;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private TextView statusText;
    private TextView logText;
    private EditText configUrlInput;
    private EditText configUsernameInput;
    private EditText configPasswordInput;
    private EditText shellyUrlInput;
    private EditText whitelistInput;
    private Button startButton;
    private Button stopButton;
    private Button saveButton;
    private Button testButton;
    private Button reloadConfigButton;
    private Button reloadLocalConfigButton;
    
    private SharedPreferences prefs;
    private TextView versionText;
    private LogUpdateReceiver logReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences("GateOpenerPrefs", MODE_PRIVATE);
        
        initViews();
        loadSettings();
        checkPermissions();
        updateServiceStatus();
    }

    private void initViews() {
        statusText = (TextView) findViewById(R.id.statusText);
        logText = (TextView) findViewById(R.id.logText);
        configUrlInput = (EditText) findViewById(R.id.configUrlInput);
        configUsernameInput = (EditText) findViewById(R.id.configUsernameInput);
        configPasswordInput = (EditText) findViewById(R.id.configPasswordInput);
        shellyUrlInput = (EditText) findViewById(R.id.shellyUrlInput);
        whitelistInput = (EditText) findViewById(R.id.whitelistInput);
        startButton = (Button) findViewById(R.id.startButton);
        stopButton = (Button) findViewById(R.id.stopButton);
        saveButton = (Button) findViewById(R.id.saveButton);
        testButton = (Button) findViewById(R.id.testButton);
        reloadConfigButton = (Button) findViewById(R.id.reloadConfigButton);
        reloadLocalConfigButton = (Button) findViewById(R.id.reloadLocalConfigButton);
        versionText = (TextView) findViewById(R.id.versionText);
        
        displayVersion();

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGateOpenerService();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopGateOpenerService();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });

        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testShellyConnection();
            }
        });

        reloadConfigButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reloadNetworkConfig();
            }
        });

        reloadLocalConfigButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reloadLocalConfig();
            }
        });
    }

    private void displayVersion() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = "Version " + pInfo.versionName + " (" + pInfo.versionCode + ")";
            versionText.setText(version);
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("Version unknown");
        }
    }

    private void reloadLocalConfig() {
        Toast.makeText(this, "Reloading local config from " + LocalConfigLoader.getConfigPath(this), Toast.LENGTH_LONG).show();
        LocalConfigLoader.getInstance(this).loadConfigFromFile();
        
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                loadSettings();
            }
        }, 1000);
    }

    private static final String DEFAULT_WHITELIST = "+32477707305\n+32480891081\n+32477783774\n+32468498861";

    private void loadSettings() {
        String configUrl = prefs.getString("config_url", "");
        String configUsername = prefs.getString("config_username", "");
        String configPassword = prefs.getString("config_password", "");
        String shellyUrl = prefs.getString("shelly_url", "http://192.168.68.80");
        String whitelist = prefs.getString("whitelist", DEFAULT_WHITELIST);
        
        configUrlInput.setText(configUrl);
        configUsernameInput.setText(configUsername);
        configPasswordInput.setText(configPassword);
        shellyUrlInput.setText(shellyUrl);
        whitelistInput.setText(whitelist);
        
        String log = prefs.getString("activity_log", "No activity yet...");
        logText.setText(log);
    }

    private void saveSettings() {
        String configUrl = configUrlInput.getText().toString().trim();
        String configUsername = configUsernameInput.getText().toString().trim();
        String configPassword = configPasswordInput.getText().toString().trim();
        String shellyUrl = shellyUrlInput.getText().toString().trim();
        String whitelist = whitelistInput.getText().toString().trim();
        
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("config_url", configUrl);
        editor.putString("config_username", configUsername);
        editor.putString("config_password", configPassword);
        editor.putString("shelly_url", shellyUrl);
        editor.putString("whitelist", whitelist);
        editor.apply();
        
        WhitelistManager.getInstance(this).reloadWhitelist();
        
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
    }

    private void reloadNetworkConfig() {
        String configUrl = configUrlInput.getText().toString().trim();
        if (configUrl.isEmpty()) {
            Toast.makeText(this, "Please enter a config URL first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String configUsername = configUsernameInput.getText().toString().trim();
        String configPassword = configPasswordInput.getText().toString().trim();
        
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("config_url", configUrl);
        editor.putString("config_username", configUsername);
        editor.putString("config_password", configPassword);
        editor.apply();
        
        Toast.makeText(this, "Reloading config...", Toast.LENGTH_SHORT).show();
        
        NetworkConfigLoader.getInstance(this).loadConfigFromNetwork();
        
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                loadSettings();
            }
        }, 2000);
    }

    private void startGateOpenerService() {
        saveSettings();
        Intent intent = new Intent(this, GateOpenerService.class);
        startService(intent);
        updateServiceStatus();
    }

    private void stopGateOpenerService() {
        Intent intent = new Intent(this, GateOpenerService.class);
        stopService(intent);
        updateServiceStatus();
    }

    private void testShellyConnection() {
        final String shellyUrl = shellyUrlInput.getText().toString().trim();
        if (shellyUrl.isEmpty()) {
            Toast.makeText(this, "Please enter Shelly URL first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "Testing connection...", Toast.LENGTH_SHORT).show();
        ActivityLogger.log(MainActivity.this, "Test button pressed - Triggering gate!");
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean success = ShellyClient.triggerGate(shellyUrl, MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            ActivityLogger.log(MainActivity.this, "Gate triggered successfully!");
                            Toast.makeText(MainActivity.this, "Success! Gate triggered.", Toast.LENGTH_LONG).show();
                        } else {
                            ActivityLogger.log(MainActivity.this, "ERROR: Failed to trigger gate");
                            Toast.makeText(MainActivity.this, "Failed to connect to Shelly", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void updateServiceStatus() {
        boolean isRunning = GateOpenerService.isRunning();
        if (isRunning) {
            statusText.setText(R.string.service_running);
            statusText.setTextColor(0xFF4CAF50);
        } else {
            statusText.setText(R.string.service_stopped);
            statusText.setTextColor(0xFFF44336);
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            
            boolean needRequest = false;
            for (String permission : permissions) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    needRequest = true;
                    break;
                }
            }
            
            if (needRequest) {
                requestPermissions(permissions, PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "Permissions required for app to function", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
        loadSettings();
        
        logReceiver = new LogUpdateReceiver();
        IntentFilter filter = new IntentFilter("com.microprojects.gateopener.LOG_UPDATE");
        registerReceiver(logReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (logReceiver != null) {
            unregisterReceiver(logReceiver);
        }
    }

    private class LogUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String log = prefs.getString("activity_log", "No activity yet...");
            logText.setText(log);
            updateServiceStatus();
        }
    }
}
