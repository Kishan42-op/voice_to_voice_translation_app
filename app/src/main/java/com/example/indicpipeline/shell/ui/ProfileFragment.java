package com.example.indicpipeline.shell.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.indicpipeline.R;
import com.example.indicpipeline.auth.ui.AuthActivity;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.shell.viewmodel.ProfileViewModel;
import com.example.indicpipeline.utils.AvatarUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textview.MaterialTextView;

public class ProfileFragment extends Fragment {
    private ProfileViewModel profileViewModel;
    private CircularProgressIndicator progressIndicator;
    private ScrollView scrollProfile;
    private MaterialTextView errorText;
    private MaterialTextView initialsView;
    private MaterialTextView nameView;
    private MaterialTextView usernameView;
    private MaterialTextView emailView;
    private MaterialButton logoutButton;

    public ProfileFragment() {
        super(R.layout.fragment_profile_shell);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_shell, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        progressIndicator = view.findViewById(R.id.progressProfile);
        scrollProfile = view.findViewById(R.id.scrollProfile);
        errorText = view.findViewById(R.id.tvProfileError);
        initialsView = view.findViewById(R.id.tvProfileInitials);
        nameView = view.findViewById(R.id.tvProfileName);
        usernameView = view.findViewById(R.id.tvProfileUsername);
        emailView = view.findViewById(R.id.tvProfileEmail);
        logoutButton = view.findViewById(R.id.btnProfileLogout);

        logoutButton.setOnClickListener(v -> performLogout());

        profileViewModel.getProfileState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }

            if (state.getStatus() == Resource.Status.LOADING) {
                showLoading(true);
                showError(null);
                return;
            }

            if (state.getStatus() == Resource.Status.ERROR) {
                showLoading(false);
                showError(state.getMessage());
                return;
            }

            if (state.getStatus() == Resource.Status.SUCCESS) {
                showLoading(false);
                showError(null);
                User user = state.getData();
                if (user != null) {
                    displayProfile(user);
                } else {
                    showError(getString(R.string.shell_profile_missing));
                }
            }
        });

        profileViewModel.getLogoutState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            if (state.getStatus() == Resource.Status.SUCCESS) {
                profileViewModel.clearLogoutState();
                navigateToAuth();
            }
            if (state.getStatus() == Resource.Status.ERROR) {
                showError(state.getMessage());
                profileViewModel.clearLogoutState();
            }
        });

        if (savedInstanceState == null) {
            profileViewModel.loadProfile();
        }
    }

    private void displayProfile(User user) {
        String initials = AvatarUtils.initials(user.getName(), user.getUsername());
        initialsView.setText(initials);
        nameView.setText(user.getName() != null ? user.getName() : "");
        usernameView.setText(user.getUsername() != null ? "@" + user.getUsername() : "");
        emailView.setText(user.getEmail() != null ? user.getEmail() : "");
        scrollProfile.setVisibility(View.VISIBLE);
    }

    private void showLoading(boolean loading) {
        progressIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        scrollProfile.setVisibility(loading ? View.GONE : View.VISIBLE);
    }

    private void showError(String message) {
        if (message == null || message.isEmpty()) {
            errorText.setVisibility(View.GONE);
            errorText.setText(null);
            return;
        }
        errorText.setVisibility(View.VISIBLE);
        errorText.setText(message);
        scrollProfile.setVisibility(View.GONE);
    }

    private void performLogout() {
        profileViewModel.logout();
    }

    private void navigateToAuth() {
        Intent intent = new Intent(requireActivity(), AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finishAffinity();
    }
}


