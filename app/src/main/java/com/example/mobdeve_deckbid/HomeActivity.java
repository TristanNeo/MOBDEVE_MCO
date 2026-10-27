package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class HomeActivity extends Activity {

    private TextView pageTitle;
    private TextView homeTab;
    private TextView watchlistTab;
    private TextView auctionsTab;
    private TextView binderTab;
    private TextView profileTab;
    private Button logoutButton;

    private int selectedTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        pageTitle = findViewById(R.id.page_title);
        homeTab = findViewById(R.id.tab_home);
        watchlistTab = findViewById(R.id.tab_watchlist);
        auctionsTab = findViewById(R.id.tab_auctions);
        binderTab = findViewById(R.id.tab_binder);
        profileTab = findViewById(R.id.tab_profile);
        logoutButton = findViewById(R.id.logout_button);

        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt("selected_tab", 0);
        }

        showTab(selectedTab);
    }

    public void selectTab(View view) {
        int id = view.getId();

        if (id == R.id.tab_home) {
            showTab(0);
        } else if (id == R.id.tab_watchlist) {
            showTab(1);
        } else if (id == R.id.tab_auctions) {
            showTab(2);
        } else if (id == R.id.tab_binder) {
            showTab(3);
        } else if (id == R.id.tab_profile) {
            showTab(4);
        }
    }

    private void showTab(int position) {
        selectedTab = position;

        TextView[] tabs = {
                homeTab,
                watchlistTab,
                auctionsTab,
                binderTab,
                profileTab
        };

        String[] titles = {
                "Home",
                "Watchlist",
                "My Auctions",
                "Binder",
                "Profile"
        };

        int[] icons = {
                R.drawable.ic_nav_home,
                R.drawable.ic_nav_watchlist,
                R.drawable.ic_nav_auction,
                R.drawable.ic_nav_binder,
                R.drawable.ic_nav_profile
        };

        int iconSize = Math.round(
                24 * getResources().getDisplayMetrics().density
        );

        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == position;

            int color = android.graphics.Color.parseColor(
                    selected ? "#D4A63A" : "#95A1B3"
            );

            android.graphics.drawable.Drawable icon =
                    getDrawable(icons[i]).mutate();

            icon.setBounds(0, 0, iconSize, iconSize);
            icon.setTint(color);

            tabs[i].setCompoundDrawables(null, icon, null, null);
            tabs[i].setTextColor(color);
            tabs[i].setBackgroundColor(
                    android.graphics.Color.TRANSPARENT
            );
            tabs[i].setSelected(selected);
        }

        pageTitle.setText(titles[position]);

        logoutButton.setVisibility(
                position == 4 ? View.VISIBLE : View.GONE
        );
    }

    public void logout(View view) {
        Intent intent = new Intent(HomeActivity.this, EntryActivity.class);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        startActivity(intent);
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("selected_tab", selectedTab);
        super.onSaveInstanceState(outState);
    }
}