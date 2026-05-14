  package com.example.indicpipeline.home.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.indicpipeline.R;
import com.example.indicpipeline.auth.ui.AuthActivity;
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.home.adapter.UserAdapter;
import com.example.indicpipeline.home.viewmodel.HomeViewModel;
import com.example.indicpipeline.models.User;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

public class HomeActivity extends AppCompatActivity {
    public static final String EXTRA_USERNAME = "extra_username";
    public static final String EXTRA_NAME = "extra_name";

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private HomeViewModel homeViewModel;
    private UserAdapter userAdapter;
    private TextInputEditText searchInput;
    private CircularProgressIndicator progressIndicator;
    private MaterialTextView messageText;
    private MaterialTextView emptyText;
    private RecyclerView usersRecyclerView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        MaterialToolbar toolbar = findViewById(R.id.toolbarHome);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_home);
        }

        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        userAdapter = new UserAdapter();

        searchInput = findViewById(R.id.etSearchUsers);
        progressIndicator = findViewById(R.id.progressHome);
        messageText = findViewById(R.id.tvHomeMessage);
        emptyText = findViewById(R.id.tvEmptyState);
        usersRecyclerView = findViewById(R.id.recyclerUsers);
        MaterialButton logoutButton = findViewById(R.id.btnLogoutHome);

        searchRunnable = () -> {
            if (homeViewModel != null) {
                homeViewModel.searchUsers(getText(searchInput));
            }
        };

        usersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        usersRecyclerView.setAdapter(userAdapter);
        usersRecyclerView.setHasFixedSize(true);

        logoutButton.setOnClickListener(v -> homeViewModel.logout());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                keepSearchFocused();
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, 250);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        homeViewModel.getSearchState().observe(this, state -> {
            if (state == null) {
                return;
            }

            if (state.getStatus() == Resource.Status.LOADING) {
                setLoading(true);
                showMessage(null, false);
                showEmptyState(false, null);
                keepSearchFocused();
                return;
            }

            if (state.getStatus() == Resource.Status.ERROR) {
                setLoading(false);
                userAdapter.submitList(null);
                showMessage(state.getMessage(), true);
                showEmptyState(false, null);
                keepSearchFocused();
                return;
            }

            if (state.getStatus() == Resource.Status.SUCCESS) {
                setLoading(false);
                showMessage(null, false);
                List<User> users = state.getData();
                userAdapter.submitList(users);
                boolean empty = users == null || users.isEmpty();
                showEmptyState(empty, getString(R.string.empty_users));
                keepSearchFocused();
            }
        });

        homeViewModel.getLogoutState().observe(this, state -> {
            if (state == null) {
                return;
            }
            if (state.getStatus() == Resource.Status.LOADING) {
                return;
            }
            if (state.getStatus() == Resource.Status.SUCCESS) {
                homeViewModel.clearLogoutState();
                openAuth();
            }
            if (state.getStatus() == Resource.Status.ERROR) {
                showMessage(state.getMessage(), true);
                homeViewModel.clearLogoutState();
            }
        });

        if (savedInstanceState == null) {
            homeViewModel.searchUsers("");
        }
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        searchInput.setEnabled(true);
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

    private void showMessage(String message, boolean isError) {
        if (message == null || message.trim().isEmpty()) {
            messageText.setVisibility(View.GONE);
            messageText.setText(null);
            return;
        }
        messageText.setVisibility(View.VISIBLE);
        messageText.setText(message);
        messageText.setTextColor(getColor(isError ? R.color.app_error : R.color.app_primary));
    }

    private void showEmptyState(boolean visible, String message) {
        emptyText.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible && message != null) {
            emptyText.setText(message);
        }
    }

    private String getText(TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString();
    }

    private void openAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        searchHandler.removeCallbacks(searchRunnable);
        super.onDestroy();
    }
}



