package com.example.indicpipeline.shell.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.indicpipeline.auth.repository.AuthRepository;
import com.example.indicpipeline.auth.repository.UserRepository;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.User;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Collections;
import java.util.List;

public class SearchViewModel extends ViewModel {
    private final UserRepository userRepository = new UserRepository();
    private final MutableLiveData<Resource<List<User>>> searchState = new MutableLiveData<>();
    private ListenerRegistration searchRegistration;
    private String currentQuery = "";

    public LiveData<Resource<List<User>>> getSearchState() {
        return searchState;
    }

    public FirebaseUser getCurrentUser() {
        return userRepository.getCurrentUser();
    }

    public void clearSearchState() {
        searchState.setValue(null);
    }

    public void searchUsers(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.equals(currentQuery) && searchRegistration != null) {
            return;
        }

        currentQuery = normalizedQuery;
        clearSearchListener();
        searchState.setValue(Resource.loading());

        FirebaseUser currentUser = getCurrentUser();
        if (currentUser == null) {
            searchState.setValue(Resource.error("User session not found."));
            return;
        }

        searchRegistration = userRepository.searchUsers(normalizedQuery, currentUser.getUid(), new UserRepository.UserSearchCallback() {
            @Override
            public void onSuccess(List<User> users) {
                searchState.postValue(Resource.success(users == null ? Collections.emptyList() : users));
            }

            @Override
            public void onError(String message) {
                searchState.postValue(Resource.error(message));
            }
        });
    }

    public void clearSearchListener() {
        if (searchRegistration != null) {
            searchRegistration.remove();
            searchRegistration = null;
        }
    }

    @Override
    protected void onCleared() {
        clearSearchListener();
        super.onCleared();
    }
}

