package com.pulse.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** 5-band system equalizer bound to the current playback session.
 *  Settings (on/off, preset, band levels) are saved and re-applied
 *  automatically whenever a new song starts, so the EQ follows the music. */
public class EqualizerActivity extends Activity {

    private Theme theme;
    private PlayerEngine engine;
    private LinearLayout bandBox;
    private final SeekBar[] bars = new SeekBar[16];
    private short[] range;
    private Switch eqSwitch;
    private GestureDetector gestureDetector;
    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        theme = Theme.current(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_equalizer);

        engine = PlayerEngine.get();

        bandBox = findViewById(R.id.bandBox);
        eqSwitch = findViewById(R.id.eqSwitch);
        findViewById(R.id.root).setBackground(Ui.gradient(theme.bgTop, theme.bgBottom));
        getWindow().setStatusBarColor(theme.bgTop);
        bandBox.setBackground(Ui.card(theme.cardTop, theme.cardBottom, theme.cardStroke));

        ((TextView) findViewById(R.id.eqTitle)).setTextColor(theme.accent);
        findViewById(R.id.btnPresets).setBackground(
                Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 20, theme.cardStroke));
        ((TextView) findViewById(R.id.btnPresets)).setTextColor(theme.text);

        findViewById(R.id.btnPresets).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPresets();
            }
        });

        eqSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                engine.setEqualizerEnabled(EqualizerActivity.this, checked);
            }
        });

        buildBands();

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > SWIPE_THRESHOLD) {
                    return false;
                }
                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    finish();
                }
                return true;
            }
        });

        findViewById(R.id.root).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return false;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-apply in case a song started while this screen was in the background.
        engine.applyEqualizer();
        refreshSwitch();
        refreshBands();
    }

    private void buildBands() {
        bandBox.removeAllViews();
        int session = engine.audioSessionId();
        if (session == 0) {
            addMessage("Start a Naat first — the equalizer binds to the active audio session.");
            eqSwitch.setEnabled(false);
            return;
        }
        eqSwitch.setEnabled(true);
        engine.applyEqualizer();
        range = engine.getBandLevelRange();
        short bands = engine.getNumberOfBands();

        for (short band = 0; band < bands; band++) {
            int centerHz = equalizerCenterHz(band);
            addBandRow(band, centerHz);
        }
        addMessage("Bands: " + bands + " • range "
                + (range[0] / 100) + " dB … +" + (range[1] / 100) + " dB");
        refreshSwitch();
        refreshBands();
    }

    /** Equalizer center frequency is not exposed by PlayerEngine; read it lazily. */
    private int equalizerCenterHz(short band) {
        try {
            Equalizer eq = new Equalizer(0, engine.audioSessionId());
            int hz = eq.getCenterFreq(band) / 1000;
            eq.release();
            return hz;
        } catch (Throwable ignored) {
            return 60 * (band + 1);
        }
    }

    private void addBandRow(final short band, int centerHz) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 8, 0, 8);

        TextView label = new TextView(this);
        label.setText(centerHz >= 1000
                ? String.format(Locale.US, "%.1fk", centerHz / 1000f)
                : centerHz + "Hz");
        label.setTextColor(theme.textDim);
        label.setTextSize(11);
        label.setWidth(Math.round(Ui.dp(46)));
        row.addView(label);

        final SeekBar bar = new SeekBar(this);
        bar.setMax(range[1] - range[0]);
        bar.setProgress(engine.getEqualizerBand(band) - range[0]);
        bar.setEnabled(engine.isEqualizerEnabled());
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(theme.accent));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(theme.accent));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.setLayoutParams(params);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    engine.setEqualizerBand(band, (short) (progress + range[0]));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        row.addView(bar);
        bars[band] = bar;
        bandBox.addView(row);
    }

    private void refreshSwitch() {
        eqSwitch.setChecked(engine.isEqualizerEnabled());
        for (SeekBar bar : bars) {
            if (bar != null) bar.setEnabled(engine.isEqualizerEnabled());
        }
    }

    private void refreshBands() {
        if (range == null) return;
        for (short band = 0; band < engine.getNumberOfBands(); band++) {
            if (bars[band] != null) {
                bars[band].setProgress(engine.getEqualizerBand(band) - range[0]);
            }
        }
    }

    private void addMessage(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(theme.textDim);
        view.setTextSize(12);
        view.setPadding(0, 10, 0, 6);
        bandBox.addView(view);
    }

    private void showPresets() {
        if (engine.getNumberOfPresets() == 0) {
            Toast.makeText(this, "Start a Naat first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            short count = engine.getNumberOfPresets();
            final String[] names = new String[count];
            for (short i = 0; i < count; i++) names[i] = engine.getPresetName(i);

            new AlertDialog.Builder(this)
                    .setTitle("Presets")
                    .setItems(names, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            engine.usePreset((short) which);
                            engine.setEqualizerPreset(EqualizerActivity.this, which);
                            refreshBands();
                        }
                    })
                    .show();
        } catch (Throwable ignored) {
        }
    }
}
