package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openEntry = new Runnable() {
        @Override
        public void run() {
            startActivity(new Intent(MainActivity.this, EntryActivity.class));
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(openEntry, 1800);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(openEntry);
        super.onPause();
    }
}
