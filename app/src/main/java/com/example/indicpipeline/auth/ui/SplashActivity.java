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
            } else if (isProfileIncomplete(user)) {
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
        intent.putExtra("extra_name", user.getName());
        intent.putExtra("extra_email", user.getEmail());
        intent.putExtra("extra_username", user.getUsername());
        if (user.getPreferredLanguage() != null) {
            intent.putExtra("extra_preferred_language_name", user.getPreferredLanguage().getName());
            intent.putExtra("extra_preferred_language_code", user.getPreferredLanguage().getCode());
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private boolean isProfileIncomplete(User user) {
        if (user == null) {
            return true;
        }
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return true;
        }
        return user.getPreferredLanguage() == null || user.getPreferredLanguage().getCode() == null || user.getPreferredLanguage().getCode().trim().isEmpty();
    }
}

