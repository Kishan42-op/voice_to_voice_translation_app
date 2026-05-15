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
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.indicpipeline.R;
import com.example.indicpipeline.contacts.friend_detail.FriendDetailActivity;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.contacts.model.UserConnectionItem;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.shell.adapter.ContactCardAdapter;
import com.example.indicpipeline.shell.viewmodel.ContactsViewModel;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import java.util.Collections;
import java.util.List;

public class ContactsFragment extends Fragment {
    private ContactsViewModel contactsViewModel;
    private ContactCardAdapter contactAdapter;
    private TextInputEditText searchInput;
    private CircularProgressIndicator progressIndicator;
    private LinearLayout emptyState;
    private MaterialTextView errorText;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

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

        contactsViewModel = new ViewModelProvider(requireActivity()).get(ContactsViewModel.class);

        searchInput = view.findViewById(R.id.etSearchContacts);
        progressIndicator = view.findViewById(R.id.progressContacts);
        emptyState = view.findViewById(R.id.tvContactsEmpty);
        errorText = view.findViewById(R.id.tvContactsError);
        RecyclerView recyclerContacts = view.findViewById(R.id.recyclerContacts);

        contactAdapter = new ContactCardAdapter(new ContactCardAdapter.OnContactActionListener() {
            @Override
            public void onContactClick(User user) {
                openFriendDetail(user);
            }

            @Override
            public void onCallClick(User user) {
                // Placeholder: will be connected to LiveKit calling in future
                openFriendDetail(user);
            }
        });

        recyclerContacts.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerContacts.setAdapter(contactAdapter);
        recyclerContacts.setHasFixedSize(true);

        searchRunnable = () -> contactsViewModel.searchContacts(getText(searchInput));

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                keepSearchFocused();
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, 300);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        contactsViewModel.getFriendsState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }

            if (state.getStatus() == Resource.Status.LOADING) {
                setLoading(true);
                showError(null);
                showEmpty(false);
                return;
            }

            if (state.getStatus() == Resource.Status.ERROR) {
                setLoading(false);
                contactAdapter.submitList(Collections.emptyList());
                showError(state.getMessage());
                showEmpty(false);
                return;
            }

            if (state.getStatus() == Resource.Status.SUCCESS) {
                setLoading(false);
                showError(null);
                List<UserConnectionItem> contacts = state.getData();
                contactAdapter.submitList(contacts == null ? Collections.emptyList() : contacts);
                boolean empty = contacts == null || contacts.isEmpty();
                showEmpty(empty);
            }
        });

        // Load initial friends list
        if (savedInstanceState == null) {
            contactsViewModel.loadFriends();
        }
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

    private void setLoading(boolean loading) {
        progressIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showEmpty(boolean visible) {
        emptyState.setVisibility(visible ? View.VISIBLE : View.GONE);
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
        return editText.getText().toString();
    }

    private void keepSearchFocused() {
        if (searchInput == null) {
            return;
        }
        searchInput.post(() -> {
            searchInput.requestFocus();
            if (searchInput.getText() != null) {
                searchInput.setSelection(searchInput.getText().length());
            }
        });
    }

    @Override
    public void onDestroyView() {
        searchHandler.removeCallbacks(searchRunnable);
        super.onDestroyView();
    }
}





