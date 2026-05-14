package com.example.indicpipeline.shell.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.indicpipeline.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AppShellActivity extends AppCompatActivity {
    private BottomNavigationView bottomNavigation;
    private HomeFragment homeFragment;
    private SearchFragment searchFragment;
    private NotificationsFragment notificationsFragment;
    private ProfileFragment profileFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_shell);

        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Initialize fragments
        FragmentManager fm = getSupportFragmentManager();
        homeFragment = (HomeFragment) fm.findFragmentByTag("home");
        if (homeFragment == null) {
            homeFragment = new HomeFragment();
        }

        searchFragment = (SearchFragment) fm.findFragmentByTag("search");
        if (searchFragment == null) {
            searchFragment = new SearchFragment();
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
            } else if (item.getItemId() == R.id.nav_search) {
                selectedFragment = searchFragment;
                tag = "search";
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

