package com.pulse.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Circular "CD" artwork that spins like a disc while a Naat is playing and
 * holds its angle when paused.
 *
 * The circle-clip + rotation approach is a reimplementation of the technique
 * used by André Mion's Music-Cover-View (Apache License 2.0,
 * https://github.com/andremion/Music-Cover-View). No code is copied verbatim;
 * this view is dependency free and draws itself with the default framework.
 */
public class DiscArtView extends View {

    /** One full turn every 22 seconds, matching a slow turntable. */
    private static final long REVOLUTION_MS = 22000L;

    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF artRect = new RectF();

    private Bitmap art;
    private float angle;
    private boolean spinning;
    private ValueAnimator spinner;
    private Theme theme = Theme.SKY;

    public DiscArtView(Context context) {
        super(context);
        init();
    }

    public DiscArtView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        glyphPaint.setTextAlign(Paint.Align.CENTER);
        glyphPaint.setFakeBoldText(true);
    }

    public void setDiscTheme(Theme theme) {
        this.theme = theme == null ? Theme.SKY : theme;
        invalidate();
    }

    /** Passing null shows the themed placeholder disc with a note glyph. */
    public void setArt(Bitmap bitmap) {
        if (bitmap == this.art) return;
        this.art = bitmap;
        this.angle = 0f;
        invalidate();
    }

    public boolean hasArt() {
        return art != null;
    }

    public void setSpinning(boolean spin) {
        spinning = spin;
        if (spin) startSpin();
        else pauseSpin();
    }

    private void startSpin() {
        if (spinner == null) {
            spinner = ValueAnimator.ofFloat(0f, 360f);
            spinner.setDuration(REVOLUTION_MS);
            spinner.setRepeatCount(ValueAnimator.INFINITE);
            spinner.setInterpolator(new LinearInterpolator());
            spinner.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    angle = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            spinner.start();
        } else if (spinner.isPaused()) {
            spinner.resume();
        } else if (!spinner.isStarted()) {
            spinner.start();
        }
    }

    private void pauseSpin() {
        if (spinner != null && spinner.isStarted() && !spinner.isPaused()) spinner.pause();
    }

    @Override
    protected void onDetachedFromWindow() {
        pauseSpin();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (spinning) startSpin();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(cx, cy) - dp(4);
        if (r <= 0) return;

        bodyPaint.setShader(new RadialGradient(cx, cy, r,
                new int[]{lighten(theme.cardTop, 0.22f), theme.cardTop, darken(theme.bgBottom, 0.30f)},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, bodyPaint);
        bodyPaint.setShader(null);

        if (art != null) {
            int save = canvas.save();
            clip.reset();
            clip.addCircle(cx, cy, r - dp(2), Path.Direction.CW);
            canvas.clipPath(clip);
            canvas.rotate(angle, cx, cy);
            float scale = Math.max((2 * r) / art.getWidth(), (2 * r) / art.getHeight());
            float aw = art.getWidth() * scale;
            float ah = art.getHeight() * scale;
            artRect.set(cx - aw / 2f, cy - ah / 2f, cx + aw / 2f, cy + ah / 2f);
            canvas.drawBitmap(art, null, artRect, artPaint);
            canvas.restoreToCount(save);
        }

        // Rim + grooves so the disc still reads as a CD when there is no art.
        ringPaint.setStrokeWidth(dp(1.2f));
        ringPaint.setColor((theme.accent & 0x00FFFFFF) | 0x40000000);
        canvas.drawCircle(cx, cy, r - dp(1.2f), ringPaint);

        ringPaint.setStrokeWidth(dp(0.7f));
        ringPaint.setColor(0x1FFFFFFF);
        for (int i = 1; i <= 4; i++) {
            canvas.drawCircle(cx, cy, r * (0.30f + i * 0.14f), ringPaint);
        }

        if (art == null) {
            glyphPaint.setColor(theme.onAccent);
            glyphPaint.setTextSize(r * 0.52f);
            canvas.drawText("♪", cx, cy + r * 0.19f, glyphPaint);
        }

        // Spindle hole in the middle, exactly like a disc.
        holePaint.setStyle(Paint.Style.FILL);
        holePaint.setColor(theme.bgBottom);
        canvas.drawCircle(cx, cy, r * 0.072f, holePaint);
        ringPaint.setStrokeWidth(dp(1.3f));
        ringPaint.setColor((theme.accent & 0x00FFFFFF) | 0x99000000);
        canvas.drawCircle(cx, cy, r * 0.072f, ringPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static int lighten(int color, float amount) {
        int r = Math.min(255, Math.round(((color >> 16) & 0xFF) + 255 * amount));
        int g = Math.min(255, Math.round(((color >> 8) & 0xFF) + 255 * amount));
        int b = Math.min(255, Math.round((color & 0xFF) + 255 * amount));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, float amount) {
        int r = Math.max(0, Math.round(((color >> 16) & 0xFF) * (1f - amount)));
        int g = Math.max(0, Math.round(((color >> 8) & 0xFF) * (1f - amount)));
        int b = Math.max(0, Math.round((color & 0xFF) * (1f - amount)));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
