package com.example.indicpipeline.auth.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.indicpipeline.MainActivity;
import com.example.indicpipeline.R;
import com.example.indicpipeline.auth.viewmodel.SessionViewModel;
import com.example.indicpipeline.core.Resource;

public class SplashActivity extends AppCompatActivity {
    private SessionViewModel sessionViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        sessionViewModel = new ViewModelProvider(this).get(SessionViewModel.class);
        sessionViewModel.getSessionState().observe(this, state -> {
            if (state == null) {
                return;
            }
            if (state.getStatus() == Resource.Status.LOADING) {
                return;
            }

            boolean loggedIn = Boolean.TRUE.equals(state.getData());
            sessionViewModel.clearSessionState();
            if (loggedIn) {
                openHome();
            } else {
                openAuth();
            }
        });

        sessionViewModel.checkSession();
    }

    private void openAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void openHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}

