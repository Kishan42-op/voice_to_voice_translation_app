package com.example.indicpipeline.shell.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.indicpipeline.auth.repository.AuthRepository;
import com.example.indicpipeline.auth.repository.UserRepository;
import com.example.indicpipeline.contacts.model.FriendContact;
import com.example.indicpipeline.contacts.model.UserConnectionItem;
import com.example.indicpipeline.contacts.repository.ContactsRepository;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.User;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContactsViewModel extends ViewModel {
    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();
    private final ContactsRepository contactsRepository = new ContactsRepository();

    private final MutableLiveData<Resource<List<UserConnectionItem>>> friendsState = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<UserConnectionItem>>> searchState = new MutableLiveData<>();
    private final MutableLiveData<Resource<User>> friendDetailState = new MutableLiveData<>();

    private ListenerRegistration friendsRegistration;
    private ListenerRegistration friendsMetadataRegistration;
    private ListenerRegistration searchRegistration;

    private String currentUserUid;
    private String currentSearchQuery = "";
    private final List<User> latestFriendData = new ArrayList<>();

    public LiveData<Resource<List<UserConnectionItem>>> getFriendsState() {
        return friendsState;
    }

    public LiveData<Resource<List<UserConnectionItem>>> getSearchState() {
        return searchState;
    }

    public LiveData<Resource<User>> getFriendDetailState() {
        return friendDetailState;
    }

    public FirebaseUser getCurrentUser() {
        return authRepository.getCurrentUser();
    }

    public void loadFriends() {
        FirebaseUser currentUser = authRepository.getCurrentUser();
        if (currentUser == null) {
            friendsState.setValue(Resource.error("User session not found."));
            return;
        }

        currentUserUid = currentUser.getUid();
        friendsState.setValue(Resource.loading());

        friendsRegistration = contactsRepository.getFriends(currentUserUid, new ContactsRepository.FriendsCallback() {
            @Override
            public void onSuccess(List<FriendContact> friends) {
                latestFriendData.clear();
                if (friends == null || friends.isEmpty()) {
                    friendsState.postValue(Resource.success(Collections.emptyList()));
                    return;
                }

                loadFriendsMetadata(friends);
            }

            @Override
            public void onError(String message) {
                friendsState.postValue(Resource.error(message));
            }
        });
    }

    public void searchContacts(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.equals(currentSearchQuery) && searchRegistration != null) {
            return;
        }

        currentSearchQuery = normalizedQuery;
        clearSearchListener();
        searchState.setValue(Resource.loading());

        FirebaseUser currentUser = authRepository.getCurrentUser();
        if (currentUser == null) {
            searchState.setValue(Resource.error("User session not found."));
            return;
        }

        currentUserUid = currentUser.getUid();

        searchRegistration = userRepository.searchUsers(normalizedQuery, currentUserUid, new UserRepository.UserSearchCallback() {
            @Override
            public void onSuccess(List<User> users) {
                // Filter to only show friends
                List<UserConnectionItem> friendMatches = new ArrayList<>();
                if (users != null) {
                    for (User user : users) {
                        if (isFriend(user.getUid())) {
                            friendMatches.add(new UserConnectionItem(user, null, null));
                        }
                    }
                }
                searchState.postValue(Resource.success(friendMatches));
            }

            @Override
            public void onError(String message) {
                searchState.postValue(Resource.error(message));
            }
        });
    }

    public void loadFriendDetail(String friendUid) {
        friendDetailState.setValue(Resource.loading());
        userRepository.getUserDocument(friendUid, new AuthRepository.AuthResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                friendDetailState.postValue(Resource.success(user));
            }

            @Override
            public void onError(String message) {
                friendDetailState.postValue(Resource.error(message));
            }
        });
    }

    public void clearSearchState() {
        searchState.setValue(null);
    }

    public void clearFriendsState() {
        friendsState.setValue(null);
    }

    private void loadFriendsMetadata(List<FriendContact> friends) {
        List<UserConnectionItem> items = new ArrayList<>(Collections.nCopies(friends.size(), null));
        int[] remaining = {friends.size()};

        for (int i = 0; i < friends.size(); i++) {
            final int index = i;
            FriendContact friend = friends.get(i);
            String friendUid = friend.getFriendUid();

            if (friendUid == null) {
                remaining[0]--;
                if (remaining[0] == 0) {
                    publishFriendsResults(items);
                }
                continue;
            }

            userRepository.getUserDocument(friendUid, new AuthRepository.AuthResultCallback<User>() {
                @Override
                public void onSuccess(User user) {
                    items.set(index, new UserConnectionItem(user, null, null));
                    remaining[0]--;
                    if (remaining[0] == 0) {
                        publishFriendsResults(items);
                    }
                }

                @Override
                public void onError(String message) {
                    remaining[0]--;
                    if (remaining[0] == 0) {
                        publishFriendsResults(items);
                    }
                }
            });
        }
    }

    private void publishFriendsResults(List<UserConnectionItem> items) {
        List<UserConnectionItem> filtered = new ArrayList<>();
        for (UserConnectionItem item : items) {
            if (item != null && item.getUser() != null) {
                filtered.add(item);
                latestFriendData.add(item.getUser());
            }
        }
        friendsState.postValue(Resource.success(filtered));
    }

    private boolean isFriend(String uid) {
        if (uid == null) return false;
        for (User user : latestFriendData) {
            if (user != null && uid.equals(user.getUid())) {
                return true;
            }
        }
        return false;
    }

    private void clearSearchListener() {
        if (searchRegistration != null) {
            searchRegistration.remove();
            searchRegistration = null;
        }
    }

    @Override
    protected void onCleared() {
        clearSearchListener();
        if (friendsRegistration != null) {
            friendsRegistration.remove();
        }
        if (friendsMetadataRegistration != null) {
            friendsMetadataRegistration.remove();
        }
        super.onCleared();
    }
}

