package com.example.indicpipeline.auth.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.indicpipeline.auth.repository.AuthRepository;
import com.example.indicpipeline.auth.repository.UserRepository;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.User;
import com.google.firebase.auth.FirebaseUser;

public class SessionViewModel extends ViewModel {
    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();
    private final MutableLiveData<Resource<User>> sessionState = new MutableLiveData<>();

    public LiveData<Resource<User>> getSessionState() {
        return sessionState;
    }

    public void clearSessionState() {
        sessionState.setValue(null);
    }

    public void checkSession() {
        sessionState.setValue(Resource.loading());
        FirebaseUser currentUser = authRepository.getCurrentUser();
        if (currentUser == null) {
            sessionState.setValue(Resource.success(null));
            return;
        }

        userRepository.getUserDocument(currentUser.getUid(), new AuthRepository.AuthResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
                    sessionState.postValue(Resource.success(new User(
                            currentUser.getUid(),
                            user.getName() != null ? user.getName() : currentUser.getDisplayName(),
                            user.getEmail() != null ? user.getEmail() : currentUser.getEmail(),
                            null,
                            user.getCreatedAt()
                    )));
                    return;
                }
                sessionState.postValue(Resource.success(user));
            }

            @Override
            public void onError(String message) {
                sessionState.postValue(Resource.success(new User(
                        currentUser.getUid(),
                        currentUser.getDisplayName(),
                        currentUser.getEmail(),
                        null,
                        null
                )));
            }
        });
    }
}

