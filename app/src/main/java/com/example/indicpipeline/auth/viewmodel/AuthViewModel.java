package com.example.indicpipeline.auth.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.indicpipeline.auth.repository.AuthRepository;
import com.example.indicpipeline.auth.repository.UserRepository;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.User;
import com.google.firebase.auth.FirebaseUser;

public class AuthViewModel extends ViewModel {
    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();

    private final MutableLiveData<Resource<User>> authState = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>> logoutState = new MutableLiveData<>();

    public LiveData<Resource<User>> getAuthState() {
        return authState;
    }

    public LiveData<Resource<Boolean>> getLogoutState() {
        return logoutState;
    }

    public void clearAuthState() {
        authState.setValue(null);
    }

    public void clearLogoutState() {
        logoutState.setValue(null);
    }

    public void login(String email, String password) {
        authState.setValue(Resource.loading());
        authRepository.login(email, password, new AuthRepository.AuthResultCallback<FirebaseUser>() {
            @Override
            public void onSuccess(FirebaseUser result) {
                loadProfileAndDispatch(result);
            }

            @Override
            public void onError(String message) {
                authState.postValue(Resource.error(message));
            }
        });
    }

    public void signup(String name, String email, String password) {
        authState.setValue(Resource.loading());
        authRepository.signUp(email, password, new AuthRepository.AuthResultCallback<FirebaseUser>() {
            @Override
            public void onSuccess(FirebaseUser result) {
                userRepository.createUserDocument(result, name, new AuthRepository.AuthResultCallback<User>() {
                    @Override
                    public void onSuccess(User user) {
                        authState.postValue(Resource.success(user));
                    }

                    @Override
                    public void onError(String message) {
                        authState.postValue(Resource.error(message));
                    }
                });
            }

            @Override
            public void onError(String message) {
                authState.postValue(Resource.error(message));
            }
        });
    }

    public void logout() {
        logoutState.setValue(Resource.loading());
        authRepository.logout();
        logoutState.setValue(Resource.success(Boolean.TRUE));
    }

    private void loadProfileAndDispatch(FirebaseUser firebaseUser) {
        if (firebaseUser == null) {
            authState.postValue(Resource.error("User session not found."));
            return;
        }

        userRepository.getUserDocument(firebaseUser.getUid(), new AuthRepository.AuthResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                authState.postValue(Resource.success(user));
            }

            @Override
            public void onError(String message) {
                User fallbackUser = new User(
                        firebaseUser.getUid(),
                        firebaseUser.getDisplayName(),
                        firebaseUser.getEmail(),
                        null
                );
                authState.postValue(Resource.success(fallbackUser));
            }
        });
    }
}

