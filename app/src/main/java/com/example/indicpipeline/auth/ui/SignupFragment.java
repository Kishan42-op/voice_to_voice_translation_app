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

import com.example.indicpipeline.R;
import com.example.indicpipeline.auth.viewmodel.AuthViewModel;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.profile.ui.UsernameSetupActivity;
import com.example.indicpipeline.shell.ui.AppShellActivity;
import com.example.indicpipeline.utils.AuthValidator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;

public class SignupFragment extends Fragment {
    private AuthViewModel authViewModel;
    private TextInputLayout nameLayout;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout confirmPasswordLayout;
    private TextInputEditText nameInput;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmPasswordInput;
    private MaterialButton signupButton;
    private CircularProgressIndicator progressIndicator;
    private MaterialTextView errorText;

    public SignupFragment() {
        super(R.layout.fragment_signup);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_signup, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        authViewModel.clearAuthState();

        nameLayout = view.findViewById(R.id.layoutName);
        emailLayout = view.findViewById(R.id.layoutEmail);
        passwordLayout = view.findViewById(R.id.layoutPassword);
        confirmPasswordLayout = view.findViewById(R.id.layoutConfirmPassword);
        nameInput = view.findViewById(R.id.etName);
        emailInput = view.findViewById(R.id.etEmail);
        passwordInput = view.findViewById(R.id.etPassword);
        confirmPasswordInput = view.findViewById(R.id.etConfirmPassword);
        signupButton = view.findViewById(R.id.btnSignup);
        progressIndicator = view.findViewById(R.id.progressAuth);
        errorText = view.findViewById(R.id.tvAuthMessage);

        view.findViewById(R.id.tvGoToLogin).setOnClickListener(v -> {
            authViewModel.clearAuthState();
            Navigation.findNavController(v).navigate(R.id.action_signupFragment_to_loginFragment);
        });
        signupButton.setOnClickListener(v -> attemptSignup());

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
                routeNext(user);
            }
        });
    }

    private void attemptSignup() {
        clearFieldErrors();

        String name = getText(nameInput);
        String email = getText(emailInput);
        String password = getText(passwordInput);
        String confirmPassword = getText(confirmPasswordInput);

        String nameError = AuthValidator.validateName(name);
        String emailError = AuthValidator.validateEmail(email);
        String passwordError = AuthValidator.validatePassword(password);
        String confirmPasswordError = AuthValidator.validateConfirmPassword(password, confirmPassword);

        boolean valid = true;
        if (nameError != null) {
            nameLayout.setError(nameError);
            valid = false;
        }
        if (emailError != null) {
            emailLayout.setError(emailError);
            valid = false;
        }
        if (passwordError != null) {
            passwordLayout.setError(passwordError);
            valid = false;
        }
        if (confirmPasswordError != null) {
            confirmPasswordLayout.setError(confirmPasswordError);
            valid = false;
        }
        if (!valid) {
            return;
        }

        authViewModel.signup(name.trim(), email.trim(), password);
    }

    private void clearFieldErrors() {
        nameLayout.setError(null);
        emailLayout.setError(null);
        passwordLayout.setError(null);
        confirmPasswordLayout.setError(null);
    }

    private String getText(TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    private void setLoading(boolean loading) {
        signupButton.setEnabled(!loading);
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

    private void routeNext(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            Intent intent = new Intent(requireContext(), UsernameSetupActivity.class);
            if (user != null) {
                intent.putExtra(UsernameSetupActivity.EXTRA_NAME, user.getName());
                intent.putExtra(UsernameSetupActivity.EXTRA_EMAIL, user.getEmail());
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            requireActivity().finishAffinity();
            return;
        }

        Intent intent = new Intent(requireContext(), AppShellActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finishAffinity();
    }
}


