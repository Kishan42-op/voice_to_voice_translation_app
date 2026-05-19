package com.example.indicpipeline.profile.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.indicpipeline.R;
import com.example.indicpipeline.LangConfig;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.language.LanguageCatalog;
import com.example.indicpipeline.models.PreferredLanguage;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.profile.viewmodel.UsernameSetupViewModel;
import com.example.indicpipeline.shell.ui.AppShellActivity;
import com.example.indicpipeline.utils.AuthValidator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

public class UsernameSetupActivity extends AppCompatActivity {
    public static final String EXTRA_NAME = "extra_name";
    public static final String EXTRA_EMAIL = "extra_email";
    public static final String EXTRA_USERNAME = "extra_username";
    public static final String EXTRA_PREFERRED_LANGUAGE_NAME = "extra_preferred_language_name";
    public static final String EXTRA_PREFERRED_LANGUAGE_CODE = "extra_preferred_language_code";

    private UsernameSetupViewModel viewModel;
    private TextInputLayout nameLayout;
    private TextInputLayout usernameLayout;
    private TextInputLayout preferredLanguageLayout;
    private TextInputEditText nameInput;
    private TextInputEditText usernameInput;
    private MaterialAutoCompleteTextView preferredLanguageInput;
    private MaterialTextView emailText;
    private MaterialTextView messageText;
    private CircularProgressIndicator progressIndicator;
    private MaterialButton saveButton;
    private List<LangConfig> supportedLanguages;
    private LangConfig selectedLanguage;

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
        preferredLanguageLayout = findViewById(R.id.layoutPreferredLanguage);
        nameInput = findViewById(R.id.etProfileName);
        usernameInput = findViewById(R.id.etUsername);
        preferredLanguageInput = findViewById(R.id.etPreferredLanguage);
        emailText = findViewById(R.id.tvProfileEmail);
        messageText = findViewById(R.id.tvProfileMessage);
        progressIndicator = findViewById(R.id.progressProfile);
        saveButton = findViewById(R.id.btnSaveProfile);

        String passedName = getIntent().getStringExtra(EXTRA_NAME);
        String passedEmail = getIntent().getStringExtra(EXTRA_EMAIL);
        String passedUsername = getIntent().getStringExtra(EXTRA_USERNAME);
        String passedLanguageName = getIntent().getStringExtra(EXTRA_PREFERRED_LANGUAGE_NAME);
        String passedLanguageCode = getIntent().getStringExtra(EXTRA_PREFERRED_LANGUAGE_CODE);
        if (!TextUtils.isEmpty(passedName)) {
            nameInput.setText(passedName);
        }
        if (!TextUtils.isEmpty(passedEmail)) {
            emailText.setText(passedEmail);
        } else {
            emailText.setText("");
        }

        if (!TextUtils.isEmpty(passedUsername)) {
            usernameInput.setText(passedUsername);
        }

        setupPreferredLanguageDropdown();
        preselectPreferredLanguage(passedLanguageName, passedLanguageCode);

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
        PreferredLanguage preferredLanguage = resolvePreferredLanguage();

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
        if (preferredLanguage == null) {
            preferredLanguageLayout.setError(getString(R.string.error_preferred_language_required));
            valid = false;
        }
        if (!valid) {
            return;
        }

        // TODO(call-session): consume user.getPreferredLanguage() when call/session initialization
        // starts so ASR, translation, and TTS resources can be loaded per user's chosen language.
        viewModel.saveProfile(name.trim(), username.trim(), preferredLanguage);
    }

    private void clearFieldErrors() {
        nameLayout.setError(null);
        usernameLayout.setError(null);
        preferredLanguageLayout.setError(null);
    }

    private String getText(TextView editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    private void setLoading(boolean loading) {
        saveButton.setEnabled(!loading);
        progressIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void setupPreferredLanguageDropdown() {
        supportedLanguages = LanguageCatalog.getSupportedLanguages();
        android.widget.ArrayAdapter<LangConfig> adapter = new android.widget.ArrayAdapter<>(
                this,
                R.layout.spinner_language_item,
                supportedLanguages
        );
        adapter.setDropDownViewResource(R.layout.spinner_language_dropdown_item);
        preferredLanguageInput.setAdapter(adapter);
        preferredLanguageInput.setThreshold(1);
        preferredLanguageInput.setOnItemClickListener((parent, view, position, id) -> {
            selectedLanguage = (LangConfig) parent.getItemAtPosition(position);
            preferredLanguageLayout.setError(null);
            preferredLanguageInput.setText(selectedLanguage.name, false);
        });
        preferredLanguageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                selectedLanguage = LanguageCatalog.findByName(s == null ? null : s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        preferredLanguageInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && selectedLanguage == null) {
                preferredLanguageInput.setText("", false);
            }
        });
    }

    private void preselectPreferredLanguage(String languageName, String languageCode) {
        LangConfig preselected = null;
        if (!TextUtils.isEmpty(languageCode)) {
            preselected = LanguageCatalog.findByCode(languageCode.trim());
        }
        if (preselected == null && !TextUtils.isEmpty(languageName)) {
            preselected = LanguageCatalog.findByName(languageName.trim());
        }
        if (preselected != null) {
            selectedLanguage = preselected;
            preferredLanguageInput.setText(preselected.name, false);
        }
    }

    private PreferredLanguage resolvePreferredLanguage() {
        if (selectedLanguage == null) {
            selectedLanguage = LanguageCatalog.findByName(getText(preferredLanguageInput));
        }
        return LanguageCatalog.toPreferredLanguage(selectedLanguage);
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


