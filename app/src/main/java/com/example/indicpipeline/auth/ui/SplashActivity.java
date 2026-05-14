package com.example.indicpipeline.auth.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.indicpipeline.R;
import com.example.indicpipeline.auth.viewmodel.SessionViewModel;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.profile.ui.UsernameSetupActivity;
import com.example.indicpipeline.shell.ui.AppShellActivity;

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

            sessionViewModel.clearSessionState();
            User user = state.getData();
            if (user == null) {
                openAuth();
            } else if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
                openUsernameSetup(user);
            } else {
                openHome();
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
        Intent intent = new Intent(this, AppShellActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void openUsernameSetup(User user) {
        Intent intent = new Intent(this, UsernameSetupActivity.class);
        intent.putExtra(UsernameSetupActivity.EXTRA_NAME, user.getName());
        intent.putExtra(UsernameSetupActivity.EXTRA_EMAIL, user.getEmail());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}

