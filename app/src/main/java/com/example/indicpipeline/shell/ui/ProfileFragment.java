package com.example.indicpipeline.shell.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
import com.example.indicpipeline.models.GlobalModelManager;
import com.example.indicpipeline.language.LanguageCatalog;
import com.example.indicpipeline.LangConfig;
import com.example.indicpipeline.utils.AvatarUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
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
    private MaterialTextView preferredLanguageView;
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
        preferredLanguageView = view.findViewById(R.id.tvProfilePreferredLanguage);
        logoutButton = view.findViewById(R.id.btnProfileLogout);

        logoutButton.setOnClickListener(v -> performLogout());
        preferredLanguageView.setOnClickListener(v -> showLanguageSelectionDialog());

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

        profileViewModel.getUpdateLanguageState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }

            if (state.getStatus() == Resource.Status.LOADING) {
                showLoading(true);
                return;
            }

            if (state.getStatus() == Resource.Status.ERROR) {
                showLoading(false);
                Snackbar.make(scrollProfile, state.getMessage(), Snackbar.LENGTH_LONG).show();
                profileViewModel.clearUpdateLanguageState();
                return;
            }

            if (state.getStatus() == Resource.Status.SUCCESS) {
                showLoading(false);
                Snackbar.make(scrollProfile, "Language updated successfully!", Snackbar.LENGTH_SHORT).show();
                profileViewModel.clearUpdateLanguageState();
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
        if (user.getPreferredLanguage() != null && user.getPreferredLanguage().getName() != null) {
            String code = user.getPreferredLanguage().getCode();
            preferredLanguageView.setText(code != null && !code.trim().isEmpty()
                    ? user.getPreferredLanguage().getName() + " (" + code + ")"
                    : user.getPreferredLanguage().getName());

            // Trigger global model preloading for the user's preferred language
            LangConfig lang = LanguageCatalog.findByName(user.getPreferredLanguage().getName());
            if (lang != null) {
                GlobalModelManager gmm = GlobalModelManager.getInstance();
                gmm.preloadLocalModels(requireContext().getApplicationContext(), lang);
                gmm.getStatus().observe(getViewLifecycleOwner(), status -> {
                    if (status != null && !status.equals("Idle")) {
                        Log.i("ProfileFragment", "Model Status: " + status);
                    }
                });
            }
        } else {
            preferredLanguageView.setText(getString(R.string.shell_profile_language_missing));
        }
        scrollProfile.setVisibility(View.VISIBLE);
    }

    private void showLanguageSelectionDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_select_language, null);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Preferred Language")
                .setView(dialogView);

        // Create language options
        java.util.List<LangConfig> languages = LanguageCatalog.getSupportedLanguages();
        String[] languageNames = new String[languages.size()];
        for (int i = 0; i < languages.size(); i++) {
            languageNames[i] = languages.get(i).name;
        }

        int selectedIndex = -1;
        User currentUser = profileViewModel.getProfileState().getValue() != null ? 
                profileViewModel.getProfileState().getValue().getData() : null;
        if (currentUser != null && currentUser.getPreferredLanguage() != null) {
            String currentLanguage = currentUser.getPreferredLanguage().getName();
            for (int i = 0; i < languageNames.length; i++) {
                if (languageNames[i].equals(currentLanguage)) {
                    selectedIndex = i;
                    break;
                }
            }
        }

        builder.setSingleChoiceItems(languageNames, selectedIndex, (dialog, which) -> {
            LangConfig selectedLanguage = languages.get(which);
            com.example.indicpipeline.models.PreferredLanguage preferredLanguage = 
                    LanguageCatalog.toPreferredLanguage(selectedLanguage);
            profileViewModel.updatePreferredLanguage(preferredLanguage);
            dialog.dismiss();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void showLoading(boolean loading) {
        progressIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        scrollProfile.setVisibility(loading ? View.GONE : View.VISIBLE);
    }

    private void showError(String message) {
        if (message == null || message.trim().isEmpty()) {
            errorText.setVisibility(View.GONE);
        } else {
            errorText.setText(message);
            errorText.setVisibility(View.VISIBLE);
        }
    }

    private void performLogout() {
        profileViewModel.logout();
    }

    private void navigateToAuth() {
        Intent intent = new Intent(getActivity(), AuthActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
