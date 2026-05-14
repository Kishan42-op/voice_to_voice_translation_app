package com.example.indicpipeline.shell.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.indicpipeline.auth.repository.AuthRepository;
import com.example.indicpipeline.auth.repository.UserRepository;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.User;
import com.google.firebase.auth.FirebaseUser;

public class ProfileViewModel extends ViewModel {
    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();
    private final MutableLiveData<Resource<User>> profileState = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>> logoutState = new MutableLiveData<>();

    public LiveData<Resource<User>> getProfileState() {
        return profileState;
    }

    public LiveData<Resource<Boolean>> getLogoutState() {
        return logoutState;
    }

    public void clearLogoutState() {
        logoutState.setValue(null);
    }

    public void clearProfileState() {
        profileState.setValue(null);
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
            }

            @Override
            public void onError(String message) {
                profileState.postValue(Resource.error(message));
            }
        });
    }

    public void logout() {
        logoutState.setValue(Resource.loading());
        authRepository.logout();
        logoutState.setValue(Resource.success(Boolean.TRUE));
    }
}

