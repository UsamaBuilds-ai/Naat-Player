package com.pulse.player;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
     * Cards show only the palette itself; long-pressing a custom card deletes it.
     * Tapping the "＋" card opens the custom theme maker.
     */
    public static View themeCards(final Context context, Theme active, final ThemePick pick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dpInt(16), dpInt(10), dpInt(16), dpInt(6));

        List<Theme> cards = new ArrayList<>(Arrays.asList(Theme.ALL));
        cards.addAll(CustomTheme.list(context));

        for (Theme theme : cards) {
            boolean selected = active != null && active.key.equals(theme.key);
            final Theme picked = theme;
            boolean custom = theme.key.startsWith("custom:");

            FrameLayout card = buildCard(context, theme, selected);
            card.setContentDescription(theme.label + (selected ? ", selected" : ""));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (pick != null) pick.onPick(picked);
                }
            });
            if (custom) {
                card.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        confirmDelete(context, picked);
                        return true;
                    }
                });
            }
            row.addView(card);
        }

        FrameLayout add = new FrameLayout(context);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                0, dpInt(104), 1f);
        if (!cards.isEmpty()) addParams.leftMargin = dpInt(10);
        add.setLayoutParams(addParams);
        GradientDrawable addBackground = new GradientDrawable();
        addBackground.setColor(0x0FFFFFFF);
        addBackground.setCornerRadius(dp(20));
        addBackground.setStroke(dpInt(1), 0x44FFFFFF);
        add.setBackground(addBackground);
        TextView addPlus = new TextView(context);
        addPlus.setText("＋");
        addPlus.setTextColor(0xFFB9CBD8);
        addPlus.setTextSize(28);
        FrameLayout.LayoutParams plusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        addPlus.setLayoutParams(plusParams);
        add.addView(addPlus);
        add.setContentDescription("Create a custom theme");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Activity activity = context instanceof Activity ? (Activity) context : null;
                if (activity == null) return;
                CustomTheme.showMakerDialog(activity, new Runnable() {
                    @Override
                    public void run() {
                        activity.recreate();
                    }
                });
            }
        });
        row.addView(add);
        return row;
    }

    private static FrameLayout buildCard(Context context, Theme theme, boolean selected) {
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

        View dot = new View(context);
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(
                dpInt(30), dpInt(30), Gravity.CENTER);
        dot.setLayoutParams(dotParams);
        dot.setBackground(oval(theme.accentSoft, theme.accent));
        card.addView(dot);
        return card;
    }

    private static void confirmDelete(final Context context, final Theme theme) {
        new android.app.AlertDialog.Builder(context)
                .setTitle("Delete theme?")
                .setMessage("This custom theme will be removed. Stock themes stay.")
                .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        CustomTheme.delete(context, theme);
                        if (Theme.current(context).key.equals(theme.key)) {
                            Theme.select(context, Theme.SKY);
                        }
                        if (context instanceof Activity) {
                            ((Activity) context).recreate();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static int dpInt(float value) {
        return Math.round(dp(value));
    }

    /** Square-centre crop of a bitmap into a circle, for cover previews. */
    public static android.graphics.Bitmap circle(android.graphics.Bitmap source) {
        if (source == null) return null;
        int size = Math.min(source.getWidth(), source.getHeight());
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(out);
        android.graphics.BitmapShader shader = new android.graphics.BitmapShader(
                source, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        float scale = size / (float) Math.min(source.getWidth(), source.getHeight());
        matrix.setScale(scale, scale);
        matrix.postTranslate((size - source.getWidth() * scale) / 2f,
                (size - source.getHeight() * scale) / 2f);
        shader.setLocalMatrix(matrix);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setShader(shader);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        return out;
    }
}
