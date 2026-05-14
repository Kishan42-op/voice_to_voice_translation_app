package com.example.indicpipeline.profile.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.indicpipeline.auth.repository.AuthRepository;
import com.example.indicpipeline.auth.repository.UserRepository;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.utils.AuthValidator;
import com.google.firebase.auth.FirebaseUser;

public class UsernameSetupViewModel extends ViewModel {
    private final UserRepository userRepository = new UserRepository();
    private final MutableLiveData<Resource<User>> profileState = new MutableLiveData<>();

    public LiveData<Resource<User>> getProfileState() {
        return profileState;
    }

    public void clearState() {
        profileState.setValue(null);
    }

    public void saveProfile(String name, String username) {
        profileState.setValue(Resource.loading());

        String nameError = AuthValidator.validateName(name);
        if (nameError != null) {
            profileState.setValue(Resource.error(nameError));
            return;
        }

        String usernameError = AuthValidator.validateUsername(username);
        if (usernameError != null) {
            profileState.setValue(Resource.error(usernameError));
            return;
        }

        FirebaseUser currentUser = userRepository.getCurrentUser();
        if (currentUser == null) {
            profileState.setValue(Resource.error("Authenticated user not found."));
            return;
        }

        userRepository.checkUsernameAvailability(username, new AuthRepository.AuthResultCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean available) {
                if (!Boolean.TRUE.equals(available)) {
                    profileState.postValue(Resource.error("Username is already taken."));
                    return;
                }

                userRepository.saveUserProfile(currentUser, name.trim(), username.trim(), new AuthRepository.AuthResultCallback<User>() {
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

            @Override
            public void onError(String message) {
                profileState.postValue(Resource.error(message));
            }
        });
    }
}

