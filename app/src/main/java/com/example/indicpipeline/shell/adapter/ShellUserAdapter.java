package com.example.indicpipeline.shell.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.indicpipeline.R;
import com.example.indicpipeline.contacts.model.RelationshipStatus;
import com.example.indicpipeline.contacts.model.UserConnectionItem;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.utils.AvatarUtils;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

public class ShellUserAdapter extends ListAdapter<UserConnectionItem, ShellUserAdapter.UserViewHolder> {
    public interface OnUserActionListener {
        void onSendRequest(String uid);

        void onAcceptRequest(String requestId);
    }

    private final OnUserActionListener listener;

    public ShellUserAdapter(OnUserActionListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<UserConnectionItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<UserConnectionItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull UserConnectionItem oldItem, @NonNull UserConnectionItem newItem) {
            User oldUser = oldItem.getUser();
            User newUser = newItem.getUser();
            return oldUser != null && newUser != null
                    && oldUser.getUid() != null
                    && oldUser.getUid().equals(newUser.getUid());
        }

        @Override
        public boolean areContentsTheSame(@NonNull UserConnectionItem oldItem, @NonNull UserConnectionItem newItem) {
            User oldUser = oldItem.getUser();
            User newUser = newItem.getUser();
            return safeEquals(oldUser != null ? oldUser.getUid() : null, newUser != null ? newUser.getUid() : null)
                    && safeEquals(oldUser != null ? oldUser.getName() : null, newUser != null ? newUser.getName() : null)
                    && safeEquals(oldUser != null ? oldUser.getEmail() : null, newUser != null ? newUser.getEmail() : null)
                    && safeEquals(oldUser != null ? oldUser.getUsername() : null, newUser != null ? newUser.getUsername() : null)
                    && safeEquals(oldUser != null ? oldUser.getCreatedAt() : null, newUser != null ? newUser.getCreatedAt() : null)
                    && oldItem.getRelationshipStatus() == newItem.getRelationshipStatus()
                    && safeEquals(oldItem.getRequestId(), newItem.getRequestId());
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
        holder.bind(getItem(position), listener);
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        private final MaterialTextView usernameView;
        private final MaterialTextView nameView;
        private final MaterialTextView emailView;
        private final MaterialTextView initialsView;
        private final MaterialCardView cardView;
        private final MaterialCardView avatarView;
        private final MaterialButton actionButton;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardUserItem);
            avatarView = itemView.findViewById(R.id.cardAvatar);
            usernameView = itemView.findViewById(R.id.tvItemUsername);
            nameView = itemView.findViewById(R.id.tvItemName);
            emailView = itemView.findViewById(R.id.tvItemEmail);
            initialsView = itemView.findViewById(R.id.tvItemInitials);
            actionButton = itemView.findViewById(R.id.btnUserAction);
        }

        void bind(UserConnectionItem item, OnUserActionListener listener) {
            User user = item.getUser();
            if (user == null) {
                return;
            }

            String initials = AvatarUtils.initials(user.getName(), user.getUsername());
            initialsView.setText(initials);

            usernameView.setText(user.getUsername() == null ? "" : "@" + user.getUsername());
            nameView.setText(user.getName() == null ? "" : user.getName());
            emailView.setText(user.getEmail() == null ? "" : user.getEmail());

            RelationshipStatus status = item.getRelationshipStatus();
            if (status == RelationshipStatus.FRIENDS) {
                bindActionButton(itemView.getContext().getString(R.string.shell_friend_request_friends), false, false, null);
            } else if (status == RelationshipStatus.REQUEST_SENT) {
                bindActionButton(itemView.getContext().getString(R.string.shell_friend_request_sent), false, false, null);
            } else if (status == RelationshipStatus.REQUEST_RECEIVED) {
                bindActionButton(itemView.getContext().getString(R.string.shell_friend_request_accept), true, true, () -> {
                    if (listener != null) {
                        listener.onAcceptRequest(item.getRequestId());
                    }
                });
            } else {
                bindActionButton(itemView.getContext().getString(R.string.shell_friend_request_send), true, false, () -> {
                    if (listener != null) {
                        listener.onSendRequest(user.getUid());
                    }
                });
            }

            cardView.setAlpha(1f);
            avatarView.setAlpha(1f);
        }

        private void bindActionButton(String text, boolean enabled, boolean prominent, Runnable clickAction) {
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setText(text);
            actionButton.setEnabled(enabled);
            actionButton.setClickable(enabled);
            actionButton.setAlpha(enabled ? 1f : 0.72f);
            actionButton.setOnClickListener(enabled && clickAction != null ? v -> clickAction.run() : null);
            if (prominent) {
                actionButton.setTextColor(actionButton.getContext().getColor(R.color.app_on_primary));
                actionButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(actionButton.getContext().getColor(R.color.app_primary)));
            } else if (enabled) {
                actionButton.setTextColor(actionButton.getContext().getColor(R.color.app_on_secondary_container));
                actionButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(actionButton.getContext().getColor(R.color.app_secondary_container)));
            } else {
                actionButton.setTextColor(actionButton.getContext().getColor(R.color.app_on_surface_variant));
                actionButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(actionButton.getContext().getColor(R.color.app_surface_variant)));
            }
        }
    }
}

