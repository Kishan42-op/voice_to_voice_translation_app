package com.example.indicpipeline.auth.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.indicpipeline.auth.repository.AuthRepository;
import com.example.indicpipeline.core.Resource;

public class SessionViewModel extends ViewModel {
    private final AuthRepository authRepository = new AuthRepository();
    private final MutableLiveData<Resource<Boolean>> sessionState = new MutableLiveData<>();

    public LiveData<Resource<Boolean>> getSessionState() {
        return sessionState;
    }

    public void clearSessionState() {
        sessionState.setValue(null);
    }

    public void checkSession() {
        sessionState.setValue(Resource.loading());
        sessionState.setValue(Resource.success(authRepository.isLoggedIn()));
    }
}

