package com.example.indicpipeline.shell.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.indicpipeline.R;
import com.example.indicpipeline.contacts.model.UserConnectionItem;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.utils.AvatarUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textview.MaterialTextView;

public class ContactCardAdapter extends ListAdapter<UserConnectionItem, ContactCardAdapter.ContactViewHolder> {
    public interface OnContactActionListener {
        void onContactClick(User user);
        void onCallClick(User user);
    }

    private final OnContactActionListener listener;

    public ContactCardAdapter(OnContactActionListener listener) {
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
                    && safeEquals(oldUser != null ? oldUser.getUsername() : null, newUser != null ? newUser.getUsername() : null);
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
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact_card, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    public static class ContactViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final MaterialCardView avatarView;
        private final MaterialTextView initialsView;
        private final MaterialTextView usernameView;
        private final MaterialTextView nameView;
        private final MaterialTextView emailView;
        private final MaterialButton callButton;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardContactItem);
            avatarView = itemView.findViewById(R.id.cardContactAvatar);
            initialsView = itemView.findViewById(R.id.tvContactInitials);
            usernameView = itemView.findViewById(R.id.tvContactUsername);
            nameView = itemView.findViewById(R.id.tvContactName);
            emailView = itemView.findViewById(R.id.tvContactEmail);
            callButton = itemView.findViewById(R.id.btnContactCall);
        }

        void bind(UserConnectionItem item, OnContactActionListener listener) {
            User user = item.getUser();
            if (user == null) {
                return;
            }

            String initials = AvatarUtils.initials(user.getName(), user.getUsername());
            initialsView.setText(initials);
            usernameView.setText(user.getUsername() == null ? "" : "@" + user.getUsername());
            nameView.setText(user.getName() == null ? "" : user.getName());
            emailView.setText(user.getEmail() == null ? "" : user.getEmail());

            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onContactClick(user);
                }
            });

            callButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCallClick(user);
                }
            });
        }
    }
}

