package com.example.indicpipeline.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.indicpipeline.R;
import com.example.indicpipeline.call.CallConfig;
import com.example.indicpipeline.call.socket.SocketManager;

/**
 * Simple settings activity to configure server URLs for debugging/testing.
 * Allows setting SIGNALING_SERVER_URL and TOKEN_SERVER_BASE_URL without recompiling.
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "IndicPipelineSettings";
    private static final String KEY_SIGNALING_URL = "signaling_url";
    private static final String KEY_TOKEN_URL = "token_url";

    private EditText etSignalingUrl, etTokenUrl;
    private Button btnSave, btnReset;
    private TextView tvSocketStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Debug Settings");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        etSignalingUrl = findViewById(R.id.etSignalingUrl);
        etTokenUrl = findViewById(R.id.etTokenUrl);
        btnSave = findViewById(R.id.btnSaveSettings);
        btnReset = findViewById(R.id.btnResetSettings);
        tvSocketStatus = findViewById(R.id.tvSocketStatus);

        loadSettings();
        updateSocketStatus();

        btnSave.setOnClickListener(v -> saveSettings());
        btnReset.setOnClickListener(v -> resetSettings());
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String signalingUrl = prefs.getString(KEY_SIGNALING_URL, CallConfig.SIGNALING_SERVER_URL);
        String tokenUrl = prefs.getString(KEY_TOKEN_URL, CallConfig.TOKEN_SERVER_BASE_URL);
        etSignalingUrl.setText(signalingUrl);
        etTokenUrl.setText(tokenUrl);
    }

    private void saveSettings() {
        String signalingUrl = etSignalingUrl.getText().toString().trim();
        String tokenUrl = etTokenUrl.getText().toString().trim();

        if (signalingUrl.isEmpty() || tokenUrl.isEmpty()) {
            Toast.makeText(this, "URLs cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(KEY_SIGNALING_URL, signalingUrl)
            .putString(KEY_TOKEN_URL, tokenUrl)
            .apply();

        // Reinitialize socket with new URL
        try {
            SocketManager.getInstance().init(signalingUrl);
            SocketManager.getInstance().connect();
            Toast.makeText(this, "Settings saved. Reconnecting...", Toast.LENGTH_SHORT).show();
            updateSocketStatus();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void resetSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        etSignalingUrl.setText(CallConfig.SIGNALING_SERVER_URL);
        etTokenUrl.setText(CallConfig.TOKEN_SERVER_BASE_URL);
        Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show();
        updateSocketStatus();
    }

    private void updateSocketStatus() {
        SocketManager mgr = SocketManager.getInstance();
        boolean connected = mgr.isConnected();
        String status = "Socket Status: " + (connected ? "✓ Connected" : "✗ Disconnected");
        status += "\nServer: " + mgr.getServerUrl();
        tvSocketStatus.setText(status);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}

