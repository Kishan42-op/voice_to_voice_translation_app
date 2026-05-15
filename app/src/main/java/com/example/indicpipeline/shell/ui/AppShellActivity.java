package com.example.indicpipeline.shell.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.indicpipeline.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AppShellActivity extends AppCompatActivity {
    private BottomNavigationView bottomNavigation;
    private HomeFragment homeFragment;
    private ContactsFragment contactsFragment;
    private NotificationsFragment notificationsFragment;
    private ProfileFragment profileFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Allow content to be drawn behind system bars and hide the status bar for a full-screen look
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_app_shell);

        // Hide only the status bar (keep navigation bar visible so bottom navigation works)
        WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsController.hide(WindowInsetsCompat.Type.statusBars());
        insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Initialize fragments
        FragmentManager fm = getSupportFragmentManager();
        homeFragment = (HomeFragment) fm.findFragmentByTag("home");
        if (homeFragment == null) {
            homeFragment = new HomeFragment();
        }

        contactsFragment = (ContactsFragment) fm.findFragmentByTag("contacts");
        if (contactsFragment == null) {
            contactsFragment = new ContactsFragment();
        }

        notificationsFragment = (NotificationsFragment) fm.findFragmentByTag("notifications");
        if (notificationsFragment == null) {
            notificationsFragment = new NotificationsFragment();
        }

        profileFragment = (ProfileFragment) fm.findFragmentByTag("profile");
        if (profileFragment == null) {
            profileFragment = new ProfileFragment();
        }

        // Show home by default
        if (savedInstanceState == null) {
            fm.beginTransaction()
                    .replace(R.id.fragmentContainer, homeFragment, "home")
                    .commit();
        }

        bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            String tag = null;

            if (item.getItemId() == R.id.nav_home) {
                selectedFragment = homeFragment;
                tag = "home";
            } else if (item.getItemId() == R.id.nav_contacts) {
                selectedFragment = contactsFragment;
                tag = "contacts";
            } else if (item.getItemId() == R.id.nav_notifications) {
                selectedFragment = notificationsFragment;
                tag = "notifications";
            } else if (item.getItemId() == R.id.nav_profile) {
                selectedFragment = profileFragment;
                tag = "profile";
            }

            if (selectedFragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        .replace(R.id.fragmentContainer, selectedFragment, tag)
                        .commit();
            }
            return true;
        });
    }
}


