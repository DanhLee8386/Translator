package com.example.translator;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Placeholder activity for saved/bookmarked translations.
 * Only reachable when the user is logged in (enforced in MainActivity).
 * Wire up real saved-translation API calls here later.
 */
public class SavedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Bản dịch đã lưu");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}