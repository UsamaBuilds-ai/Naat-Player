package com.pulse.player;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

public class SplashActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private EqualizerView visualizer;

    private final Runnable openLibrary = new Runnable() {
        @Override
        public void run() {
            if (isFinishing()) return;
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        getWindow().setStatusBarColor(Theme.current(this).bgTop);
        getWindow().setNavigationBarColor(Theme.current(this).bgTop);

        View glyph = findViewById(R.id.splashGlyph);
        TextView brand = findViewById(R.id.splashBrand);
        TextView tagline = findViewById(R.id.splashTagline);
        visualizer = findViewById(R.id.splashVisualizer);
        visualizer.setActive(true);

        glyph.setScaleX(0.7f);
        glyph.setScaleY(0.7f);
        glyph.animate().scaleX(1f).scaleY(1f).setDuration(600)
                .setInterpolator(new OvershootInterpolator()).start();
        brand.animate().alpha(1f).translationY(0f).setDuration(420).start();
        tagline.animate().alpha(1f).setStartDelay(160).setDuration(420).start();
        ui.postDelayed(openLibrary, 1250);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(openLibrary);
        if (visualizer != null) visualizer.setActive(false);
        super.onDestroy();
    }
}
