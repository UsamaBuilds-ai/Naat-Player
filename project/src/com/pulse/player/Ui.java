package com.pulse.player;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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

    // ------------------------------------------------------------ theme cards

    /** Called with the colour card the user tapped. */
    public interface ThemePick {
        void onPick(Theme theme);
    }

    /**
     * Builds the colour-card picker used by both the library and Settings.
     * Cards show only the palette itself - the theme name stays in the
     * accessibility description so it is still announced by screen readers.
     */
    public static View themeCards(Context context, Theme active, final ThemePick pick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dpInt(16), dpInt(10), dpInt(16), dpInt(6));

        for (final Theme theme : Theme.ALL) {
            boolean selected = active != null && active.key.equals(theme.key);

            FrameLayout card = new FrameLayout(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, dpInt(104), 1f);
            if (theme != Theme.ALL[0]) params.leftMargin = dpInt(10);
            card.setLayoutParams(params);

            GradientDrawable background = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{theme.bgTop, theme.bgBottom});
            background.setCornerRadius(dp(20));
            background.setStroke(dpInt(selected ? 3 : 1),
                    selected ? theme.accent : ((theme.accent & 0x00FFFFFF) | 0x33000000));
            card.setBackground(background);

            // Accent dot so each card reads as a full palette, not a plain square.
            View dot = new View(context);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(
                    dpInt(30), dpInt(30), Gravity.CENTER);
            dot.setLayoutParams(dotParams);
            dot.setBackground(oval(theme.accentSoft, theme.accent));
            card.addView(dot);

            card.setContentDescription(theme.label + (selected ? ", selected" : ""));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (pick != null) pick.onPick(theme);
                }
            });
            row.addView(card);
        }
        return row;
    }

    private static int dpInt(float value) {
        return Math.round(dp(value));
    }
}
