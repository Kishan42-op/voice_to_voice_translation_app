package com.example.indicpipeline.auth.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.indicpipeline.MainActivity;
import com.example.indicpipeline.R;
import com.example.indicpipeline.auth.viewmodel.AuthViewModel;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.utils.AuthValidator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;

public class LoginFragment extends Fragment {
    private AuthViewModel authViewModel;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private MaterialButton loginButton;
    private CircularProgressIndicator progressIndicator;
    private MaterialTextView errorText;

    public LoginFragment() {
        super(R.layout.fragment_login);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        authViewModel.clearAuthState();

        emailLayout = view.findViewById(R.id.layoutEmail);
        passwordLayout = view.findViewById(R.id.layoutPassword);
        emailInput = view.findViewById(R.id.etEmail);
        passwordInput = view.findViewById(R.id.etPassword);
        loginButton = view.findViewById(R.id.btnLogin);
        progressIndicator = view.findViewById(R.id.progressAuth);
        errorText = view.findViewById(R.id.tvAuthMessage);

        view.findViewById(R.id.tvGoToSignup).setOnClickListener(v -> {
            authViewModel.clearAuthState();
            Navigation.findNavController(v).navigate(R.id.action_loginFragment_to_signupFragment);
        });
        loginButton.setOnClickListener(v -> attemptLogin());

        authViewModel.getAuthState().observe(getViewLifecycleOwner(), state -> {
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
                authViewModel.clearAuthState();
                openHome();
            }
        });
    }

    private void attemptLogin() {
        clearFieldErrors();

        String email = getText(emailInput);
        String password = getText(passwordInput);

        String emailError = AuthValidator.validateEmail(email);
        String passwordError = AuthValidator.validatePassword(password);

        boolean valid = true;
        if (emailError != null) {
            emailLayout.setError(emailError);
            valid = false;
        }
        if (passwordError != null) {
            passwordLayout.setError(passwordError);
            valid = false;
        }
        if (!valid) {
            return;
        }

        authViewModel.login(email.trim(), password);
    }

    private void clearFieldErrors() {
        emailLayout.setError(null);
        passwordLayout.setError(null);
    }

    private String getText(TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        progressIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showMessage(String message, boolean isError) {
        if (TextUtils.isEmpty(message)) {
            errorText.setVisibility(View.GONE);
            errorText.setText(null);
            return;
        }
        errorText.setVisibility(View.VISIBLE);
        errorText.setText(message);
        errorText.setTextColor(requireContext().getColor(isError ? R.color.app_error : R.color.app_primary));
    }

    private void openHome() {
        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finishAffinity();
    }
}


