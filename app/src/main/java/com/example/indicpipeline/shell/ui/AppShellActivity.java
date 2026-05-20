package com.example.indicpipeline.shell.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.indicpipeline.R;
import com.example.indicpipeline.settings.SettingsActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AppShellActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "app_shell_prefs";
    private static final String KEY_NOTIFICATIONS_PROMPTED = "notifications_prompted";

    private BottomNavigationView bottomNavigation;
    private HomeFragment homeFragment;
    private ContactsFragment contactsFragment;
    private NotificationsFragment notificationsFragment;
    private ProfileFragment profileFragment;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                markNotificationPrompted();
            });

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

        requestNotificationPermissionIfNeeded();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            markNotificationPrompted();
            return;
        }

        if (wasNotificationPrompted()) {
            return;
        }

        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private boolean wasNotificationPrompted() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return preferences.getBoolean(KEY_NOTIFICATIONS_PROMPTED, false);
    }

    private void markNotificationPrompted() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        preferences.edit().putBoolean(KEY_NOTIFICATIONS_PROMPTED, true).apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, "Debug Settings")
            .setIcon(android.R.drawable.ic_menu_preferences)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}


