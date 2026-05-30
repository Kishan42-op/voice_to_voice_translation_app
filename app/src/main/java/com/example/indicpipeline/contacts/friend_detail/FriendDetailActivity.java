package com.example.indicpipeline.contacts.friend_detail;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.indicpipeline.R;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.shell.viewmodel.ContactsViewModel;
import com.example.indicpipeline.shell.viewmodel.FriendRequestViewModel;
import com.example.indicpipeline.utils.AvatarUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textview.MaterialTextView;

public class FriendDetailActivity extends AppCompatActivity {
    private ContactsViewModel viewModel;
    private FriendRequestViewModel friendRequestViewModel;
    private CircularProgressIndicator progressIndicator;
    private MaterialCardView avatarCard;
    private MaterialTextView initialsView;
    private MaterialTextView usernameView;
    private MaterialTextView nameView;
    private MaterialTextView emailView;
    private MaterialButton callButton;
    private MaterialButton messageButton;
    private MaterialToolbar toolbar;
    private boolean isFriend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_detail);

        viewModel = new ViewModelProvider(this).get(ContactsViewModel.class);
        friendRequestViewModel = new ViewModelProvider(this).get(FriendRequestViewModel.class);
        friendRequestViewModel.setContext(this);

        toolbar = findViewById(R.id.toolbarFriendDetail);
        progressIndicator = findViewById(R.id.progressFriendDetail);
        avatarCard = findViewById(R.id.cardFriendAvatar);
        initialsView = findViewById(R.id.tvFriendDetailInitials);
        usernameView = findViewById(R.id.tvFriendDetailUsername);
        nameView = findViewById(R.id.tvFriendDetailName);
        emailView = findViewById(R.id.tvFriendDetailEmail);
        callButton = findViewById(R.id.btnFriendDetailCall);
        messageButton = findViewById(R.id.btnFriendDetailMessage);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        String friendUid = getIntent().getStringExtra("friend_uid");
        String friendName = getIntent().getStringExtra("friend_name");
        String friendUsername = getIntent().getStringExtra("friend_username");
        String friendEmail = getIntent().getStringExtra("friend_email");
        isFriend = getIntent().getBooleanExtra("is_friend", true); // Default to true for backwards compatibility

        // Display from intent data first
        if (friendName != null) {
            nameView.setText(friendName);
        }
        if (friendUsername != null) {
            usernameView.setText("@" + friendUsername);
        }
        if (friendEmail != null) {
            emailView.setText(friendEmail);
        }
        if (friendName != null && friendUsername != null) {
            String initials = AvatarUtils.initials(friendName, friendUsername);
            initialsView.setText(initials);
        }

        // Setup buttons based on friendship status
        setupActionButtons(friendUid, friendName, isFriend);

        messageButton.setOnClickListener(v -> {
            // Placeholder for future messaging
        });

        // Load full friend details from Firestore
        if (friendUid != null) {
            progressIndicator.setVisibility(View.VISIBLE);
            viewModel.loadFriendDetail(friendUid);

            viewModel.getFriendDetailState().observe(this, state -> {
                if (state == null) {
                    return;
                }

                if (state.getStatus() == Resource.Status.LOADING) {
                    progressIndicator.setVisibility(View.VISIBLE);
                    return;
                }

                progressIndicator.setVisibility(View.GONE);

                if (state.getStatus() == Resource.Status.SUCCESS) {
                    User user = state.getData();
                    if (user != null) {
                        displayFriendDetails(user);
                    }
                }
            });
        }
    }

    private void setupActionButtons(String friendUid, String friendName, boolean isFriend) {
        if (isFriend) {
            // Show call button for friends
            callButton.setVisibility(View.VISIBLE);
            callButton.setText(R.string.friend_detail_call_button);
            callButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, com.example.indicpipeline.ui.call.OutgoingCallActivity.class);
                intent.putExtra("targetUid", friendUid);
                intent.putExtra("targetName", friendName);
                startActivity(intent);
            });
        } else {
            // Show send friend request button for non-friends
            callButton.setVisibility(View.VISIBLE);
            callButton.setText(R.string.shell_friend_request_send);
            callButton.setOnClickListener(v -> {
                friendRequestViewModel.sendFriendRequest(friendUid);
            });
        }
    }

    private void displayFriendDetails(User user) {
        String initials = AvatarUtils.initials(user.getName(), user.getUsername());
        initialsView.setText(initials);
        nameView.setText(user.getName() != null ? user.getName() : "");
        usernameView.setText(user.getUsername() != null ? "@" + user.getUsername() : "");
        emailView.setText(user.getEmail() != null ? user.getEmail() : "");
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}

