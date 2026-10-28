package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class HomeActivity extends Activity {

    private AccountStore.User user;
    private static final int PICK_PROFILE_PHOTO = 501;
    private TextView pageTitle;
    private TextView homeTab;
    private TextView watchlistTab;
    private TextView auctionsTab;
    private TextView binderTab;
    private TextView profileTab;
    private Button logoutButton;

    private int selectedTab = 0;
    private BinderController binderController;
    private AuctionsController auctionsController;
    private final android.os.Handler clockHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updateClocks = new Runnable() {
        @Override
        public void run() {
            if (auctionsController != null) auctionsController.updateTimes();
            clockHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try (AccountStore store = new AccountStore(this)) { user = store.current(); }
        if (user == null) {
            startActivity(new Intent(this, EntryActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_home);

        pageTitle = findViewById(R.id.page_title);
        homeTab = findViewById(R.id.tab_home);
        watchlistTab = findViewById(R.id.tab_watchlist);
        auctionsTab = findViewById(R.id.tab_auctions);
        binderTab = findViewById(R.id.tab_binder);
        profileTab = findViewById(R.id.tab_profile);
        logoutButton = findViewById(R.id.logout_button);

        binderController = new BinderController(this);
        auctionsController = new AuctionsController(this);
        findViewById(R.id.profile_shortcut).setOnClickListener(v -> showTab(4));
        findViewById(R.id.home_profile_card).setOnClickListener(v -> showTab(3));
        findViewById(R.id.home_profile_card).setClipToOutline(true);
        findViewById(R.id.home_explore_banner).setClipToOutline(true);
        findViewById(R.id.upload_photo).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_PROFILE_PHOTO);
        });
        refreshProfile();

        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt("selected_tab", 0);
            auctionsController.setStatus(savedInstanceState.getInt("auction_status", 0));
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
        findViewById(R.id.profile_content).setVisibility(position == 4 ? View.VISIBLE : View.GONE);

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
                "Auctions",
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
        pageTitle.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
        findViewById(R.id.dashboard_content).setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        findViewById(R.id.auctions_content).setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        findViewById(R.id.watchlist_content).setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        if (position == 0 || position == 1 || position == 2) auctionsController.refresh();
        android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(pageTitle.getWindowToken(), 0);
        findViewById(R.id.page_content).requestFocus();
        findViewById(R.id.binder_content).setVisibility(position == 3 ? View.VISIBLE : View.GONE);
        if (position == 3) binderController.refresh();
        ((android.widget.ScrollView) findViewById(R.id.home_scroll)).scrollTo(0, 0);

        logoutButton.setVisibility(
                position == 4 ? View.VISIBLE : View.GONE
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (user == null) return;
        refreshProfile();
        if (binderController != null && selectedTab == 3) binderController.refresh();
        if (auctionsController != null) auctionsController.refresh();
        clockHandler.removeCallbacks(updateClocks);
        clockHandler.post(updateClocks);
    }

    @Override
    protected void onPause() {
        clockHandler.removeCallbacks(updateClocks);
        super.onPause();
    }

    public void openTab(int position) {
        showTab(position);
    }

    public void openAuctions(int category, int status) {
        showTab(2);
        auctionsController.filter(category, status);
    }

    public void logout(View view) {
        AccountStore.logout(this);
        Intent intent = new Intent(HomeActivity.this, EntryActivity.class);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        startActivity(intent);
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("selected_tab", selectedTab);
        if (auctionsController != null) outState.putInt("auction_status", auctionsController.getStatus());
        super.onSaveInstanceState(outState);
    }
    private void refreshProfile() {
        try (AccountStore store = new AccountStore(this)) { user = store.current(); }
        if (user == null) return;
        ((TextView) findViewById(R.id.home_username)).setText(user.username);
        ((TextView) findViewById(R.id.profile_username)).setText(user.username);
        ((TextView) findViewById(R.id.profile_email)).setText(user.email);
        ((TextView) findViewById(R.id.profile_birthday)).setText(user.birthday);
        String initial = user.username.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        ((TextView) findViewById(R.id.header_initial)).setText(initial);
        ((TextView) findViewById(R.id.profile_initial)).setText(initial);
        android.graphics.Bitmap photo = user.photo;
        int[] ids = {R.id.header_photo, R.id.profile_photo, R.id.greeting_photo};
        for (int id : ids) {
            android.widget.ImageView image = findViewById(id);
            image.setImageBitmap(photo);
            image.setVisibility(photo == null ? View.INVISIBLE : View.VISIBLE);
            image.setClipToOutline(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PROFILE_PHOTO || resultCode != RESULT_OK || data == null || data.getData() == null || user == null) return;
        final long accountId = user.id;
        findViewById(R.id.upload_photo).setEnabled(false);
        new Thread(() -> {
            android.graphics.Bitmap photo = null;
            boolean saved = false;
            try (AccountStore store = new AccountStore(this)) {
                photo = ProfileImages.load(this, data.getData());
                saved = store.savePhoto(accountId, photo);
            } catch (Exception e) { saved = false; }
            final boolean success = saved;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                findViewById(R.id.upload_photo).setEnabled(true);
                if (success) refreshProfile();
                else android.widget.Toast.makeText(this, "Could not save that photo. Try another image.", android.widget.Toast.LENGTH_LONG).show();
            });
        }).start();
    }

}
