package com.example.indicpipeline.shell.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.indicpipeline.R;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.utils.AvatarUtils;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textview.MaterialTextView;

public class ShellUserAdapter extends ListAdapter<User, ShellUserAdapter.UserViewHolder> {
    public ShellUserAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<User> DIFF_CALLBACK = new DiffUtil.ItemCallback<User>() {
        @Override
        public boolean areItemsTheSame(@NonNull User oldItem, @NonNull User newItem) {
            return oldItem.getUid() != null && oldItem.getUid().equals(newItem.getUid());
        }

        @Override
        public boolean areContentsTheSame(@NonNull User oldItem, @NonNull User newItem) {
            return safeEquals(oldItem.getUid(), newItem.getUid())
                    && safeEquals(oldItem.getName(), newItem.getName())
                    && safeEquals(oldItem.getEmail(), newItem.getEmail())
                    && safeEquals(oldItem.getUsername(), newItem.getUsername())
                    && safeEquals(oldItem.getCreatedAt(), newItem.getCreatedAt());
        }

        private boolean safeEquals(Object a, Object b) {
            if (a == null) {
                return b == null;
            }
            return a.equals(b);
        }
    };

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_shell, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private final MaterialTextView usernameView;
        private final MaterialTextView nameView;
        private final MaterialTextView emailView;
        private final MaterialTextView initialsView;
        private final MaterialCardView cardView;
        private final MaterialCardView avatarView;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardUserItem);
            avatarView = itemView.findViewById(R.id.cardAvatar);
            usernameView = itemView.findViewById(R.id.tvItemUsername);
            nameView = itemView.findViewById(R.id.tvItemName);
            emailView = itemView.findViewById(R.id.tvItemEmail);
            initialsView = itemView.findViewById(R.id.tvItemInitials);
        }

        void bind(User user) {
            String initials = AvatarUtils.initials(user.getName(), user.getUsername());
            initialsView.setText(initials);

            usernameView.setText(user.getUsername() == null ? "" : "@" + user.getUsername());
            nameView.setText(user.getName() == null ? "" : user.getName());
            emailView.setText(user.getEmail() == null ? "" : user.getEmail());
            cardView.setAlpha(1f);
        }
    }
}

