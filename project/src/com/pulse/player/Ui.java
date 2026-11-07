package com.pulse.player;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.SeekBar;

/** Runtime drawables so a theme change repaints everything without extra resource sets. */
public final class Ui {

    private Ui() {
    }

    public static GradientDrawable gradient(int top, int bottom) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, bottom});
        return d;
    }

    public static GradientDrawable card(int top, int bottom, int stroke) {
        GradientDrawable d = gradient(top, bottom);
        d.setCornerRadius(dp(24));
        d.setStroke(Math.max(1, Math.round(dp(1))), stroke);
        return d;
    }

    public static GradientDrawable oval(int top, int bottom) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.BL_TR,
                new int[]{top, bottom});
        d.setShape(GradientDrawable.OVAL);
        return d;
    }

    public static GradientDrawable round(int color, int radiusDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != 0) d.setStroke(Math.max(1, Math.round(dp(1))), stroke);
        return d;
    }

    public static GradientDrawable art(int top, int bottom, int radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.BL_TR,
                new int[]{top, bottom});
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    public static void tintSeekBar(SeekBar bar, Theme theme) {
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(theme.accent));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(theme.accent));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(
                (theme.accent & 0x00FFFFFF) | 0x55000000));
    }

    public static void show(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public static float dp(float value) {
        return value * android.content.res.Resources.getSystem().getDisplayMetrics().density;
    }
}
