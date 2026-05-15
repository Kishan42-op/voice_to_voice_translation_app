package com.example.indicpipeline.shell.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.indicpipeline.auth.repository.AuthRepository;
import com.example.indicpipeline.auth.repository.UserRepository;
import com.example.indicpipeline.utils.NetworkConnectivityChecker;
import com.example.indicpipeline.contacts.model.FriendRequest;
import com.example.indicpipeline.contacts.model.IncomingFriendRequestItem;
import com.example.indicpipeline.contacts.model.RelationshipStatus;
import com.example.indicpipeline.contacts.model.UserConnectionItem;
import com.example.indicpipeline.contacts.repository.ContactsRepository;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.User;
import android.content.Context;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class FriendRequestViewModel extends ViewModel {
    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();
    private final ContactsRepository contactsRepository = new ContactsRepository();
    private NetworkConnectivityChecker connectivityChecker;

    private final MutableLiveData<Resource<List<UserConnectionItem>>> searchState = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<IncomingFriendRequestItem>>> incomingRequestsState = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>> actionState = new MutableLiveData<>();

    private final List<User> latestSearchUsers = new ArrayList<>();
    private final Map<String, FriendRequest> incomingRequestsBySender = new HashMap<>();
    private final Map<String, FriendRequest> outgoingRequestsByReceiver = new HashMap<>();
    private final Set<String> friendIds = new HashSet<>();

    private ListenerRegistration incomingRequestsRegistration;
    private ListenerRegistration outgoingRequestsRegistration;
    private ListenerRegistration friendsRegistration;
    private ListenerRegistration searchRegistration;

    private String currentUserUid;
    private String currentQuery = "";
    private boolean relationshipObserversStarted = false;

    public void setContext(Context context) {
        if (connectivityChecker == null) {
            connectivityChecker = new NetworkConnectivityChecker(context);
        }
    }

    private boolean checkNetworkAndShowError() {
        if (connectivityChecker != null && !connectivityChecker.isConnected()) {
            actionState.setValue(Resource.error("No internet connection. Please check your WiFi or mobile data."));
            return true;
        }
        return false;
    }

    public LiveData<Resource<List<UserConnectionItem>>> getSearchState() {
        return searchState;
    }

    public LiveData<Resource<List<IncomingFriendRequestItem>>> getIncomingRequestsState() {
        return incomingRequestsState;
    }

    public LiveData<Resource<Boolean>> getActionState() {
        return actionState;
    }

    public FirebaseUser getCurrentUser() {
        return authRepository.getCurrentUser();
    }

    public void clearSearchState() {
        searchState.setValue(null);
    }

    public void clearIncomingRequestsState() {
        incomingRequestsState.setValue(null);
    }

    public void clearActionState() {
        actionState.setValue(null);
    }

    public void searchUsers(String query) {
        if (checkNetworkAndShowError()) {
            searchState.setValue(Resource.error("No internet connection. Please check your WiFi or mobile data."));
            return;
        }

        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.equals(currentQuery) && searchRegistration != null) {
            return;
        }

        currentQuery = normalizedQuery;
        clearSearchListener();
        searchState.setValue(Resource.loading());

        FirebaseUser currentUser = authRepository.getCurrentUser();
        if (currentUser == null) {
            searchState.setValue(Resource.error("User session not found."));
            return;
        }

        currentUserUid = currentUser.getUid();
        ensureRelationshipObservers();

        searchRegistration = userRepository.searchUsers(normalizedQuery, currentUserUid, new UserRepository.UserSearchCallback() {
            @Override
            public void onSuccess(List<User> users) {
                latestSearchUsers.clear();
                if (users != null) {
                    latestSearchUsers.addAll(users);
                }
                publishSearchResults();
            }

            @Override
            public void onError(String message) {
                searchState.postValue(Resource.error(message));
            }
        });
    }

    public void observeIncomingRequests() {
        if (checkNetworkAndShowError()) {
            incomingRequestsState.setValue(Resource.error("No internet connection. Please check your WiFi or mobile data."));
            return;
        }

        FirebaseUser currentUser = authRepository.getCurrentUser();
        if (currentUser == null) {
            incomingRequestsState.setValue(Resource.error("User session not found."));
            return;
        }

        currentUserUid = currentUser.getUid();
        ensureRelationshipObservers();
        if (!relationshipObserversStarted) {
            incomingRequestsState.setValue(Resource.loading());
        }
    }

    public void sendFriendRequest(String receiverUid) {
        if (checkNetworkAndShowError()) {
            return;
        }

        FirebaseUser currentUser = authRepository.getCurrentUser();
        if (currentUser == null) {
            actionState.setValue(Resource.error("User session not found."));
            return;
        }

        currentUserUid = currentUser.getUid();
        ensureRelationshipObservers();
        actionState.setValue(Resource.loading());
        contactsRepository.sendFriendRequest(currentUserUid, receiverUid, new ContactsRepository.RequestCallback() {
            @Override
            public void onSuccess(FriendRequest request) {
                actionState.postValue(Resource.success(Boolean.TRUE));
                publishSearchResults();
            }

            @Override
            public void onError(String message) {
                actionState.postValue(Resource.error(message));
            }
        });
    }

    public void acceptFriendRequest(String requestId) {
        if (checkNetworkAndShowError()) {
            return;
        }

        FirebaseUser currentUser = authRepository.getCurrentUser();
        if (currentUser == null) {
            actionState.setValue(Resource.error("User session not found."));
            return;
        }

        currentUserUid = currentUser.getUid();
        ensureRelationshipObservers();
        actionState.setValue(Resource.loading());
        contactsRepository.acceptFriendRequest(requestId, currentUserUid, new ContactsRepository.VoidCallback() {
            @Override
            public void onSuccess() {
                actionState.postValue(Resource.success(Boolean.TRUE));
                publishSearchResults();
            }

            @Override
            public void onError(String message) {
                actionState.postValue(Resource.error(message));
            }
        });
    }

    public void rejectFriendRequest(String requestId) {
        if (checkNetworkAndShowError()) {
            return;
        }

        FirebaseUser currentUser = authRepository.getCurrentUser();
        if (currentUser == null) {
            actionState.setValue(Resource.error("User session not found."));
            return;
        }

        currentUserUid = currentUser.getUid();
        ensureRelationshipObservers();
        actionState.setValue(Resource.loading());
        contactsRepository.rejectFriendRequest(requestId, currentUserUid, new ContactsRepository.VoidCallback() {
            @Override
            public void onSuccess() {
                actionState.postValue(Resource.success(Boolean.TRUE));
                publishSearchResults();
            }

            @Override
            public void onError(String message) {
                actionState.postValue(Resource.error(message));
            }
        });
    }

    private void ensureRelationshipObservers() {
        if (relationshipObserversStarted || currentUserUid == null) {
            return;
        }

        relationshipObserversStarted = true;

        incomingRequestsRegistration = contactsRepository.getFriendRequests(currentUserUid, new ContactsRepository.FriendRequestsCallback() {
            @Override
            public void onSuccess(List<FriendRequest> requests) {
                incomingRequestsBySender.clear();
                List<FriendRequest> safeRequests = requests == null ? Collections.emptyList() : requests;
                for (FriendRequest request : safeRequests) {
                    if (request != null && request.getSenderUid() != null) {
                        incomingRequestsBySender.put(request.getSenderUid(), request);
                    }
                }
                publishIncomingRequests(safeRequests);
                publishSearchResults();
            }

            @Override
            public void onError(String message) {
                incomingRequestsState.postValue(Resource.error(message));
            }
        });

        outgoingRequestsRegistration = contactsRepository.getOutgoingFriendRequests(currentUserUid, new ContactsRepository.FriendRequestsCallback() {
            @Override
            public void onSuccess(List<FriendRequest> requests) {
                outgoingRequestsByReceiver.clear();
                List<FriendRequest> safeRequests = requests == null ? Collections.emptyList() : requests;
                for (FriendRequest request : safeRequests) {
                    if (request != null && request.getReceiverUid() != null) {
                        outgoingRequestsByReceiver.put(request.getReceiverUid(), request);
                    }
                }
                publishSearchResults();
            }

            @Override
            public void onError(String message) {
                searchState.postValue(Resource.error(message));
            }
        });

        friendsRegistration = contactsRepository.getFriends(currentUserUid, new ContactsRepository.FriendsCallback() {
            @Override
            public void onSuccess(List<com.example.indicpipeline.contacts.model.FriendContact> friends) {
                friendIds.clear();
                if (friends != null) {
                    for (com.example.indicpipeline.contacts.model.FriendContact friend : friends) {
                        if (friend != null && friend.getFriendUid() != null) {
                            friendIds.add(friend.getFriendUid());
                        }
                    }
                }
                publishSearchResults();
            }

            @Override
            public void onError(String message) {
                searchState.postValue(Resource.error(message));
            }
        });
    }

    private void publishSearchResults() {
        if (currentUserUid == null) {
            searchState.postValue(Resource.error("User session not found."));
            return;
        }

        List<UserConnectionItem> mappedUsers = new ArrayList<>();
        for (User user : latestSearchUsers) {
            if (user == null || user.getUid() == null) {
                continue;
            }
            RelationshipStatus relationshipStatus = resolveRelationshipStatus(user.getUid());
            String requestId = resolveRequestId(user.getUid(), relationshipStatus);
            mappedUsers.add(new UserConnectionItem(user, relationshipStatus, requestId));
        }

        searchState.postValue(Resource.success(mappedUsers));
    }

    private void publishIncomingRequests(List<FriendRequest> requests) {
        if (currentUserUid == null) {
            incomingRequestsState.postValue(Resource.error("User session not found."));
            return;
        }

        if (requests == null || requests.isEmpty()) {
            incomingRequestsState.postValue(Resource.success(Collections.emptyList()));
            return;
        }

        List<IncomingFriendRequestItem> items = new ArrayList<>(Collections.nCopies(requests.size(), null));
        AtomicInteger remaining = new AtomicInteger(requests.size());

        for (int index = 0; index < requests.size(); index++) {
            FriendRequest request = requests.get(index);
            final int itemIndex = index;
            if (request == null || request.getSenderUid() == null) {
                if (remaining.decrementAndGet() == 0) {
                    incomingRequestsState.postValue(Resource.success(collectNonNull(items)));
                }
                continue;
            }

            userRepository.getUserDocument(request.getSenderUid(), new AuthRepository.AuthResultCallback<>() {
                @Override
                public void onSuccess(User sender) {
                    if (sender == null) {
                        sender = new User(request.getSenderUid(), request.getSenderUid(), null, null, null);
                    }
                    items.set(itemIndex, new IncomingFriendRequestItem(request, sender));
                    if (remaining.decrementAndGet() == 0) {
                        incomingRequestsState.postValue(Resource.success(collectNonNull(items)));
                    }
                }

                @Override
                public void onError(String message) {
                    User fallbackSender = new User(request.getSenderUid(), request.getSenderUid(), null, null, null);
                    items.set(itemIndex, new IncomingFriendRequestItem(request, fallbackSender));
                    if (remaining.decrementAndGet() == 0) {
                        incomingRequestsState.postValue(Resource.success(collectNonNull(items)));
                    }
                }
            });
        }
    }

    private List<IncomingFriendRequestItem> collectNonNull(List<IncomingFriendRequestItem> items) {
        List<IncomingFriendRequestItem> filtered = new ArrayList<>();
        for (IncomingFriendRequestItem item : items) {
            if (item != null) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private RelationshipStatus resolveRelationshipStatus(String otherUid) {
        if (currentUserUid == null || otherUid == null) {
            return RelationshipStatus.NOT_CONNECTED;
        }
        if (currentUserUid.equals(otherUid)) {
            return RelationshipStatus.SELF;
        }
        if (friendIds.contains(otherUid)) {
            return RelationshipStatus.FRIENDS;
        }

        FriendRequest incoming = incomingRequestsBySender.get(otherUid);
        if (incoming != null && FriendRequest.STATUS_PENDING.equals(incoming.getStatus())) {
            return RelationshipStatus.REQUEST_RECEIVED;
        }

        FriendRequest outgoing = outgoingRequestsByReceiver.get(otherUid);
        if (outgoing != null && FriendRequest.STATUS_PENDING.equals(outgoing.getStatus())) {
            return RelationshipStatus.REQUEST_SENT;
        }

        return RelationshipStatus.NOT_CONNECTED;
    }

    private String resolveRequestId(String otherUid, RelationshipStatus status) {
        if (status == RelationshipStatus.REQUEST_RECEIVED) {
            FriendRequest request = incomingRequestsBySender.get(otherUid);
            return request != null ? request.getRequestId() : null;
        }
        if (status == RelationshipStatus.REQUEST_SENT) {
            FriendRequest request = outgoingRequestsByReceiver.get(otherUid);
            return request != null ? request.getRequestId() : null;
        }
        return null;
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
        if (incomingRequestsRegistration != null) {
            incomingRequestsRegistration.remove();
        }
        if (outgoingRequestsRegistration != null) {
            outgoingRequestsRegistration.remove();
        }
        if (friendsRegistration != null) {
            friendsRegistration.remove();
        }
        super.onCleared();
    }
}



