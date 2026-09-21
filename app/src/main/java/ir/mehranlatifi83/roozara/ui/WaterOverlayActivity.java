package ir.mehranlatifi83.roozara.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import ir.mehranlatifi83.roozara.R;
import ir.mehranlatifi83.roozara.manager.WaterReminderManager;
import ir.mehranlatifi83.roozara.receiver.WaterReminderReceiver;

public class WaterOverlayActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setShowWhenLocked(true);
        setTurnScreenOn(true);

        setContentView(R.layout.activity_water_overlay);

        int slot = getIntent().getIntExtra(WaterReminderReceiver.EXTRA_SLOT, 0);
        setupMessage(slot);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finish(); }
        });

        MaterialButton btnDone = findViewById(R.id.btn_water_done);
        btnDone.setOnClickListener(v -> finish());
    }

    private void setupMessage(int slot) {
        int safe = WaterReminderManager.safeSlot(slot);
        ((TextView) findViewById(R.id.text_overlay_title))
                .setText(WaterReminderManager.TITLES[safe]);
        ((TextView) findViewById(R.id.text_overlay_body))
                .setText(WaterReminderManager.TEXTS[safe]);
    }
}
