package com.example.indicpipeline.shell.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.indicpipeline.auth.repository.AuthRepository;
import com.example.indicpipeline.auth.repository.UserRepository;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.PreferredLanguage;
import com.example.indicpipeline.models.User;
import com.google.firebase.auth.FirebaseUser;

public class ProfileViewModel extends ViewModel {
    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();
    private final MutableLiveData<Resource<User>> profileState = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>> logoutState = new MutableLiveData<>();
    private final MutableLiveData<Resource<User>> updateLanguageState = new MutableLiveData<>();

    public LiveData<Resource<User>> getProfileState() {
        return profileState;
    }

    public LiveData<Resource<Boolean>> getLogoutState() {
        return logoutState;
    }

    public LiveData<Resource<User>> getUpdateLanguageState() {
        return updateLanguageState;
    }

    public void clearLogoutState() {
        logoutState.setValue(null);
    }

    public void clearProfileState() {
        profileState.setValue(null);
    }

    public void clearUpdateLanguageState() {
        updateLanguageState.setValue(null);
    }

    public void loadProfile() {
        profileState.setValue(Resource.loading());
        FirebaseUser currentUser = authRepository.getCurrentUser();
        if (currentUser == null) {
            profileState.setValue(Resource.error("User session not found."));
            return;
        }

        userRepository.getUserDocument(currentUser.getUid(), new AuthRepository.AuthResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                profileState.postValue(Resource.success(user));
                // Model preloading will be triggered from the Fragment using the user's language
            }

            @Override
            public void onError(String message) {
                profileState.postValue(Resource.error(message));
            }
        });
    }

    public void updatePreferredLanguage(PreferredLanguage newLanguage) {
        updateLanguageState.setValue(Resource.loading());
        FirebaseUser currentUser = authRepository.getCurrentUser();
        if (currentUser == null) {
            updateLanguageState.setValue(Resource.error("User session not found."));
            return;
        }

        userRepository.updatePreferredLanguage(currentUser.getUid(), newLanguage, new AuthRepository.AuthResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                updateLanguageState.postValue(Resource.success(user));
                // Reload profile to update the UI
                loadProfile();
            }

            @Override
            public void onError(String message) {
                updateLanguageState.postValue(Resource.error(message));
            }
        });
    }

    public void logout() {
        logoutState.setValue(Resource.loading());
        authRepository.logout();
        logoutState.setValue(Resource.success(Boolean.TRUE));
    }
}

