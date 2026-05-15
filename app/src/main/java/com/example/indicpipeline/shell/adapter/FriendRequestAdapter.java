package com.example.indicpipeline.shell.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.indicpipeline.R;
import com.example.indicpipeline.contacts.model.IncomingFriendRequestItem;
import com.example.indicpipeline.contacts.model.FriendRequest;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.utils.AvatarUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textview.MaterialTextView;

public class FriendRequestAdapter extends ListAdapter<IncomingFriendRequestItem, FriendRequestAdapter.ViewHolder> {
    public interface OnRequestActionListener {
        void onAccept(String requestId);

        void onReject(String requestId);
    }

    private static final DiffUtil.ItemCallback<IncomingFriendRequestItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<IncomingFriendRequestItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull IncomingFriendRequestItem oldItem, @NonNull IncomingFriendRequestItem newItem) {
            FriendRequest oldRequest = oldItem.getRequest();
            FriendRequest newRequest = newItem.getRequest();
            if (oldRequest == null || newRequest == null) {
                return false;
            }
            return oldRequest.getRequestId() != null && oldRequest.getRequestId().equals(newRequest.getRequestId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull IncomingFriendRequestItem oldItem, @NonNull IncomingFriendRequestItem newItem) {
            FriendRequest oldRequest = oldItem.getRequest();
            FriendRequest newRequest = newItem.getRequest();
            User oldUser = oldItem.getSender();
            User newUser = newItem.getSender();
            return safeEquals(oldRequest != null ? oldRequest.getRequestId() : null, newRequest != null ? newRequest.getRequestId() : null)
                    && safeEquals(oldRequest != null ? oldRequest.getStatus() : null, newRequest != null ? newRequest.getStatus() : null)
                    && safeEquals(oldRequest != null ? oldRequest.getSenderUid() : null, newRequest != null ? newRequest.getSenderUid() : null)
                    && safeEquals(oldRequest != null ? oldRequest.getReceiverUid() : null, newRequest != null ? newRequest.getReceiverUid() : null)
                    && safeEquals(oldUser != null ? oldUser.getUid() : null, newUser != null ? newUser.getUid() : null)
                    && safeEquals(oldUser != null ? oldUser.getName() : null, newUser != null ? newUser.getName() : null)
                    && safeEquals(oldUser != null ? oldUser.getUsername() : null, newUser != null ? newUser.getUsername() : null)
                    && safeEquals(oldUser != null ? oldUser.getEmail() : null, newUser != null ? newUser.getEmail() : null);
        }

        private boolean safeEquals(Object first, Object second) {
            if (first == null) {
                return second == null;
            }
            return first.equals(second);
        }
    };

    private final OnRequestActionListener listener;

    public FriendRequestAdapter(OnRequestActionListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final MaterialCardView avatarView;
        private final MaterialTextView initialsView;
        private final MaterialTextView nameView;
        private final MaterialTextView usernameView;
        private final MaterialTextView messageView;
        private final MaterialButton acceptButton;
        private final MaterialButton rejectButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardFriendRequestItem);
            avatarView = itemView.findViewById(R.id.cardFriendAvatar);
            initialsView = itemView.findViewById(R.id.tvFriendInitials);
            nameView = itemView.findViewById(R.id.tvFriendName);
            usernameView = itemView.findViewById(R.id.tvFriendUsername);
            messageView = itemView.findViewById(R.id.tvFriendMessage);
            acceptButton = itemView.findViewById(R.id.btnAcceptRequest);
            rejectButton = itemView.findViewById(R.id.btnRejectRequest);
        }

        void bind(IncomingFriendRequestItem item, OnRequestActionListener listener) {
            if (item == null || item.getRequest() == null || item.getSender() == null) {
                return;
            }

            User sender = item.getSender();
            FriendRequest request = item.getRequest();
            initialsView.setText(AvatarUtils.initials(sender.getName(), sender.getUsername()));
            nameView.setText(sender.getName() != null ? sender.getName() : "Unknown user");
            usernameView.setText(sender.getUsername() != null ? "@" + sender.getUsername() : "");
            messageView.setText("Sent you a friend request");

            acceptButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAccept(request.getRequestId());
                }
            });
            rejectButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReject(request.getRequestId());
                }
            });

            cardView.setAlpha(1f);
            avatarView.setAlpha(1f);
        }
    }
}


