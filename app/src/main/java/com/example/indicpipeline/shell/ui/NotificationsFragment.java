package com.example.indicpipeline.shell.ui;

import android.os.Bundle;
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
import com.example.indicpipeline.contacts.model.IncomingFriendRequestItem;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.shell.adapter.FriendRequestAdapter;
import com.example.indicpipeline.shell.viewmodel.FriendRequestViewModel;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textview.MaterialTextView;

public class NotificationsFragment extends Fragment {
    private FriendRequestViewModel friendRequestViewModel;
    private FriendRequestAdapter requestAdapter;
    private CircularProgressIndicator progressIndicator;
    private MaterialTextView errorText;
    private LinearLayout emptyState;
    private RecyclerView recyclerView;

    public NotificationsFragment() {
        super(R.layout.fragment_notifications_shell);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications_shell, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        friendRequestViewModel = new ViewModelProvider(requireActivity()).get(FriendRequestViewModel.class);
        friendRequestViewModel.setContext(requireContext());
        requestAdapter = new FriendRequestAdapter(new FriendRequestAdapter.OnRequestActionListener() {
            @Override
            public void onAccept(String requestId) {
                friendRequestViewModel.acceptFriendRequest(requestId);
            }

            @Override
            public void onReject(String requestId) {
                friendRequestViewModel.rejectFriendRequest(requestId);
            }
        });

        progressIndicator = view.findViewById(R.id.progressNotifications);
        errorText = view.findViewById(R.id.tvNotificationsError);
        emptyState = view.findViewById(R.id.layoutNotificationsEmpty);
        recyclerView = view.findViewById(R.id.recyclerFriendRequests);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(requestAdapter);
        recyclerView.setHasFixedSize(true);

        friendRequestViewModel.getIncomingRequestsState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }

            if (state.getStatus() == Resource.Status.LOADING) {
                showLoading(true);
                showError(null);
                showEmpty(false);
                recyclerView.setVisibility(View.GONE);
                return;
            }

            if (state.getStatus() == Resource.Status.ERROR) {
                showLoading(false);
                showError(state.getMessage());
                showEmpty(false);
                recyclerView.setVisibility(View.GONE);
                return;
            }

            if (state.getStatus() == Resource.Status.SUCCESS) {
                showLoading(false);
                showError(null);
                java.util.List<IncomingFriendRequestItem> requests = state.getData();
                boolean empty = requests == null || requests.isEmpty();
                requestAdapter.submitList(requests == null ? java.util.Collections.emptyList() : requests);
                showEmpty(empty);
                recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
            }
        });

        friendRequestViewModel.getActionState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            if (state.getStatus() == Resource.Status.LOADING) {
                showLoading(true);
            }
            if (state.getStatus() == Resource.Status.ERROR) {
                showLoading(false);
                showError(state.getMessage());
            }
            if (state.getStatus() == Resource.Status.SUCCESS) {
                showLoading(false);
                showError(null);
            }
        });

        friendRequestViewModel.observeIncomingRequests();
    }

    private void showLoading(boolean loading) {
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
}

