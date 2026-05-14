package com.example.indicpipeline.shell.ui;

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
import com.example.indicpipeline.core.Resource;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.shell.adapter.ShellUserAdapter;
import com.example.indicpipeline.shell.viewmodel.SearchViewModel;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

public class SearchFragment extends Fragment {
    private SearchViewModel searchViewModel;
    private ShellUserAdapter userAdapter;
    private TextInputEditText searchInput;
    private CircularProgressIndicator progressIndicator;
    private MaterialTextView emptyText;
    private MaterialTextView errorText;
    private RecyclerView usersRecyclerView;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    public SearchFragment() {
        super(R.layout.fragment_search);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        searchViewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        userAdapter = new ShellUserAdapter();

        searchInput = view.findViewById(R.id.etSearchUsers);
        progressIndicator = view.findViewById(R.id.progressSearch);
        emptyText = view.findViewById(R.id.tvSearchEmpty);
        errorText = view.findViewById(R.id.tvSearchError);
        usersRecyclerView = view.findViewById(R.id.recyclerSearchUsers);

        usersRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        usersRecyclerView.setAdapter(userAdapter);
        usersRecyclerView.setHasFixedSize(true);

        searchRunnable = () -> searchViewModel.searchUsers(getText(searchInput));

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

        searchViewModel.getSearchState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }

            if (state.getStatus() == Resource.Status.LOADING) {
                setLoading(true);
                showError(null);
                showEmpty(false);
                keepSearchFocused();
                return;
            }

            if (state.getStatus() == Resource.Status.ERROR) {
                setLoading(false);
                userAdapter.submitList(null);
                showError(state.getMessage());
                showEmpty(false);
                keepSearchFocused();
                return;
            }

            if (state.getStatus() == Resource.Status.SUCCESS) {
                setLoading(false);
                showError(null);
                List<User> users = state.getData();
                userAdapter.submitList(users);
                boolean empty = users == null || users.isEmpty();
                showEmpty(empty && searchInput.getText() != null && searchInput.getText().toString().trim().isEmpty());
                keepSearchFocused();
            }
        });

        // Initial search
        if (savedInstanceState == null) {
            searchViewModel.searchUsers("");
        }
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        searchInput.setEnabled(true);
    }

    private void showEmpty(boolean visible) {
        emptyText.setVisibility(visible ? View.VISIBLE : View.GONE);
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

