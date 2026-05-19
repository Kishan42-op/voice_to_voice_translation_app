package com.example.indicpipeline.shell.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.indicpipeline.R;
import com.example.indicpipeline.contacts.friend_detail.FriendDetailActivity;
import com.example.indicpipeline.contacts.model.RelationshipStatus;
import com.example.indicpipeline.contacts.model.UserConnectionItem;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.shell.adapter.ContactCardAdapter;
import com.example.indicpipeline.shell.adapter.ShellUserAdapter;
import com.example.indicpipeline.shell.viewmodel.ContactsViewModel;
import com.example.indicpipeline.shell.viewmodel.FriendRequestViewModel;
import com.example.indicpipeline.ui.call.OutgoingCallActivity;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContactsFragment extends Fragment {
    private FriendRequestViewModel friendRequestViewModel;
    private ContactsViewModel contactsViewModel;

    private ShellUserAdapter usersAdapter;
    private ContactCardAdapter friendsAdapter;

    private TextInputEditText searchInput;
    private CircularProgressIndicator usersProgress;
    private CircularProgressIndicator friendsProgress;
    private MaterialTextView errorText;
    private MaterialTextView usersEmptyText;
    private MaterialTextView friendsEmptyText;
    private RecyclerView recyclerUsers;
    private RecyclerView recyclerFriends;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private String currentSearchQuery = "";

    public ContactsFragment() {
        super(R.layout.fragment_contacts);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        friendRequestViewModel = new ViewModelProvider(requireActivity()).get(FriendRequestViewModel.class);
        contactsViewModel = new ViewModelProvider(requireActivity()).get(ContactsViewModel.class);
        friendRequestViewModel.setContext(requireContext());

        searchInput = view.findViewById(R.id.etSearchContacts);
        errorText = view.findViewById(R.id.tvContactsError);
        usersProgress = view.findViewById(R.id.progressUsers);
        friendsProgress = view.findViewById(R.id.progressFriends);
        usersEmptyText = view.findViewById(R.id.tvUsersEmpty);
        friendsEmptyText = view.findViewById(R.id.tvFriendsEmpty);
        recyclerUsers = view.findViewById(R.id.recyclerUsers);
        recyclerFriends = view.findViewById(R.id.recyclerFriends);

        usersAdapter = new ShellUserAdapter(new ShellUserAdapter.OnUserActionListener() {
            @Override
            public void onSendRequest(String uid) {
                friendRequestViewModel.sendFriendRequest(uid);
            }

            @Override
            public void onAcceptRequest(String requestId) {
                friendRequestViewModel.acceptFriendRequest(requestId);
            }

            @Override
            public void onUserClick(User user) {
                openFriendDetail(user);
            }
        });

        friendsAdapter = new ContactCardAdapter(new ContactCardAdapter.OnContactActionListener() {
            @Override
            public void onContactClick(User user) {
                openFriendDetail(user);
            }

            @Override
            public void onCallClick(User user) {
                startCall(user);
            }
        });

        recyclerUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerUsers.setAdapter(usersAdapter);
        recyclerUsers.setHasFixedSize(true);

        recyclerFriends.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerFriends.setAdapter(friendsAdapter);
        recyclerFriends.setHasFixedSize(true);

        searchRunnable = () -> {
            currentSearchQuery = getText(searchInput);
            friendRequestViewModel.searchUsers(currentSearchQuery);
        };

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, 250);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        friendRequestViewModel.getSearchState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;

            if (state.getStatus() == Resource.Status.LOADING) {
                setUsersLoading(true);
                showError(null);
                showUsersEmpty(true);
                usersEmptyText.setText(R.string.loading_users);
                recyclerUsers.setVisibility(View.GONE);
                return;
            }

            if (state.getStatus() == Resource.Status.ERROR) {
                setUsersLoading(false);
                usersAdapter.submitList(Collections.emptyList());
                showError(state.getMessage());
                showUsersEmpty(false);
                recyclerUsers.setVisibility(View.GONE);
                return;
            }

            if (state.getStatus() == Resource.Status.SUCCESS) {
                setUsersLoading(false);
                showError(null);
                renderUsers(state.getData());
            }
        });

        contactsViewModel.getFriendsState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;

            if (state.getStatus() == Resource.Status.LOADING) {
                setFriendsLoading(true);
                showError(null);
                showFriendsEmpty(true);
                friendsEmptyText.setText(R.string.loading_friends);
                recyclerFriends.setVisibility(View.GONE);
                return;
            }

            if (state.getStatus() == Resource.Status.ERROR) {
                setFriendsLoading(false);
                friendsAdapter.submitList(Collections.emptyList());
                showError(state.getMessage());
                showFriendsEmpty(false);
                recyclerFriends.setVisibility(View.GONE);
                return;
            }

            if (state.getStatus() == Resource.Status.SUCCESS) {
                setFriendsLoading(false);
                showError(null);
                renderFriends(state.getData());
            }
        });

        friendRequestViewModel.getActionState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;

            if (state.getStatus() == Resource.Status.ERROR) {
                setUsersLoading(false);
                setFriendsLoading(false);
                showError(state.getMessage());
            }

            if (state.getStatus() == Resource.Status.SUCCESS) {
                showError(null);
                friendRequestViewModel.searchUsers(currentSearchQuery);
                contactsViewModel.loadFriends();
            }
        });

        if (savedInstanceState == null) {
            currentSearchQuery = getText(searchInput);
            friendRequestViewModel.searchUsers(currentSearchQuery);
            contactsViewModel.loadFriends();
        }
    }

    private void renderUsers(List<UserConnectionItem> items) {
        List<UserConnectionItem> safeItems = items == null ? Collections.emptyList() : items;
        List<UserConnectionItem> visibleUsers = new ArrayList<>();
        for (UserConnectionItem item : safeItems) {
            if (item == null || item.getUser() == null) {
                continue;
            }
            if (item.getRelationshipStatus() != RelationshipStatus.FRIENDS) {
                visibleUsers.add(item);
            }
        }

        usersAdapter.submitList(visibleUsers);
        boolean empty = visibleUsers.isEmpty();
        showUsersEmpty(empty);
        usersEmptyText.setText(getUsersEmptyMessage());
        recyclerUsers.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void renderFriends(List<UserConnectionItem> items) {
        List<UserConnectionItem> safeItems = items == null ? Collections.emptyList() : items;
        friendsAdapter.submitList(safeItems);
        boolean empty = safeItems.isEmpty();
        friendsEmptyText.setText(getString(R.string.contacts_friends_empty));
        showFriendsEmpty(empty);
        recyclerFriends.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private String getUsersEmptyMessage() {
        if (currentSearchQuery == null || currentSearchQuery.trim().isEmpty()) {
            return getString(R.string.contacts_users_empty_default);
        }
        return getString(R.string.shell_search_no_results);
    }

    private void openFriendDetail(User user) {
        if (user == null || user.getUid() == null) {
            return;
        }
        Intent intent = new Intent(requireActivity(), FriendDetailActivity.class);
        intent.putExtra("friend_uid", user.getUid());
        intent.putExtra("friend_username", user.getUsername());
        intent.putExtra("friend_name", user.getName());
        intent.putExtra("friend_email", user.getEmail());
        startActivity(intent);
    }

    private void startCall(User user) {
        if (user == null || user.getUid() == null) {
            return;
        }
        Intent intent = new Intent(requireActivity(), OutgoingCallActivity.class);
        intent.putExtra("targetUid", user.getUid());
        intent.putExtra("targetName", user.getName());
        startActivity(intent);
    }

    private void setUsersLoading(boolean loading) {
        usersProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void setFriendsLoading(boolean loading) {
        friendsProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showUsersEmpty(boolean visible) {
        usersEmptyText.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showFriendsEmpty(boolean visible) {
        friendsEmptyText.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        if (message == null || message.isEmpty()) {
            errorText.setVisibility(View.GONE);
            errorText.setText(null);
            return;
        }
        errorText.setVisibility(View.VISIBLE);
        errorText.setText(message);
    }

    private String getText(TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    @Override
    public void onDestroyView() {
        searchHandler.removeCallbacks(searchRunnable);
        super.onDestroyView();
    }
}





