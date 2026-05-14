package com.example.indicpipeline.profile.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.indicpipeline.R;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.profile.viewmodel.UsernameSetupViewModel;
import com.example.indicpipeline.shell.ui.AppShellActivity;
import com.example.indicpipeline.utils.AuthValidator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.google.android.material.appbar.MaterialToolbar;

public class UsernameSetupActivity extends AppCompatActivity {
    public static final String EXTRA_NAME = "extra_name";
    public static final String EXTRA_EMAIL = "extra_email";

    private UsernameSetupViewModel viewModel;
    private TextInputLayout nameLayout;
    private TextInputLayout usernameLayout;
    private TextInputEditText nameInput;
    private TextInputEditText usernameInput;
    private MaterialTextView emailText;
    private MaterialTextView messageText;
    private CircularProgressIndicator progressIndicator;
    private MaterialButton saveButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_username_setup);

        MaterialToolbar toolbar = findViewById(R.id.toolbarUsernameSetup);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_username_setup);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        viewModel = new ViewModelProvider(this).get(UsernameSetupViewModel.class);

        nameLayout = findViewById(R.id.layoutProfileName);
        usernameLayout = findViewById(R.id.layoutUsername);
        nameInput = findViewById(R.id.etProfileName);
        usernameInput = findViewById(R.id.etUsername);
        emailText = findViewById(R.id.tvProfileEmail);
        messageText = findViewById(R.id.tvProfileMessage);
        progressIndicator = findViewById(R.id.progressProfile);
        saveButton = findViewById(R.id.btnSaveProfile);

        String passedName = getIntent().getStringExtra(EXTRA_NAME);
        String passedEmail = getIntent().getStringExtra(EXTRA_EMAIL);
        if (!TextUtils.isEmpty(passedName)) {
            nameInput.setText(passedName);
        }
        if (!TextUtils.isEmpty(passedEmail)) {
            emailText.setText(passedEmail);
        } else {
            emailText.setText("");
        }

        saveButton.setOnClickListener(v -> attemptSave());

        viewModel.getProfileState().observe(this, state -> {
            if (state == null) {
                return;
            }

            if (state.getStatus() == Resource.Status.LOADING) {
                setLoading(true);
                showMessage(null, false);
                return;
            }

            if (state.getStatus() == Resource.Status.ERROR) {
                setLoading(false);
                showMessage(state.getMessage(), true);
                return;
            }

            if (state.getStatus() == Resource.Status.SUCCESS) {
                setLoading(false);
                User user = state.getData();
                viewModel.clearState();
                openHome(user);
            }
        });
    }

    private void attemptSave() {
        clearFieldErrors();

        String name = getText(nameInput);
        String username = getText(usernameInput);

        String nameError = AuthValidator.validateName(name);
        String usernameError = AuthValidator.validateUsername(username);

        boolean valid = true;
        if (nameError != null) {
            nameLayout.setError(nameError);
            valid = false;
        }
        if (usernameError != null) {
            usernameLayout.setError(usernameError);
            valid = false;
        }
        if (!valid) {
            return;
        }

        viewModel.saveProfile(name.trim(), username.trim());
    }

    private void clearFieldErrors() {
        nameLayout.setError(null);
        usernameLayout.setError(null);
    }

    private String getText(TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    private void setLoading(boolean loading) {
        saveButton.setEnabled(!loading);
        progressIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showMessage(String message, boolean isError) {
        if (TextUtils.isEmpty(message)) {
            messageText.setVisibility(View.GONE);
            messageText.setText(null);
            return;
        }
        messageText.setVisibility(View.VISIBLE);
        messageText.setText(message);
        messageText.setTextColor(getColor(isError ? R.color.app_error : R.color.app_primary));
    }

    private void openHome(User user) {
        Intent intent = new Intent(this, AppShellActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (user != null) {
            intent.putExtra("username", user.getUsername());
            intent.putExtra("name", user.getName());
        }
        startActivity(intent);
        finishAffinity();
    }
}






