package com.pulse.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Animated audio visualizer for Naat playback. */
public class EqualizerView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] heights = new float[8];
    private final float[] targets = new float[8];
    private final float[] velocities = new float[8];
    private final int[] colors = {
            0xFF4FC3F7, 0xFF29B6F6, 0xFF03A9F4, 0xFF00ACC1,
            0xFF0097A7, 0xFF00838F, 0xFF006064, 0xFF4FC3F7
    };

    private boolean active;
    private long lastFrameTime;

    public EqualizerView(Context context) {
        super(context);
        init();
    }

    public EqualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EqualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < heights.length; i++) {
            heights[i] = 0;
            targets[i] = 0;
            velocities[i] = 0;
        }
        lastFrameTime = System.currentTimeMillis();
    }

    /** Call from the activity ticker while a Naat is playing. */
    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            for (int i = 0; i < heights.length; i++) {
                targets[i] = 0;
                velocities[i] = 0;
            }
            postInvalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!active) return;

        long now = System.currentTimeMillis();
        float dt = (now - lastFrameTime) / 1000f;
        lastFrameTime = now;
        if (dt > 0.1f) dt = 0.1f;

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float barW = width / (float) (heights.length * 2 + 2);
        float maxH = height * 0.65f;

        for (int i = 0; i < heights.length; i++) {
            // random target so bars keep dancing
            targets[i] = 0.25f + (float) Math.random() * 0.75f;
            // spring toward target
            float diff = targets[i] - heights[i];
            velocities[i] += diff * 14f * dt;
            velocities[i] *= 0.55f; // damping
            heights[i] += velocities[i] * dt;

            float barH = heights[i] * maxH;
            float x = (i * 2 + 1) * barW;
            float y = height / 2f - barH / 2f;

            paint.setColor(colors[i % colors.length]);
            canvas.drawRoundRect(x, y, x + barW, y + barH, barW / 2f, barW / 2f, paint);
        }

        postInvalidateDelayed(33); // ~30 fps
    }

    @Override
    protected void onDetachedFromWindow() {
        active = false;
        super.onDetachedFromWindow();
    }
}
