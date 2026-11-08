package com.pulse.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * User-created themes. A custom theme picks a background gradient and an accent;
 * everything else (cards, strokes, text) is derived for contrast.
 */
public final class CustomTheme {

    private static final String KEY = "custom_themes";
    private static final String FILE = "pulse_prefs";

    private CustomTheme() {
    }

    public static List<Theme> list(Context context) {
        List<Theme> themes = new ArrayList<>();
        try {
            JSONArray array = read(context);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                Theme theme = themeFrom(object);
                if (theme != null) themes.add(theme);
            }
        } catch (Exception ignored) {
        }
        return themes;
    }

    public static Theme find(Context context, String key) {
        if (key == null || !key.startsWith("custom:")) return null;
        String target = key.substring("custom:".length());
        for (Theme theme : list(context)) {
            if (target.equals(theme.key.substring("custom:".length()))) return theme;
        }
        return null;
    }

    public static boolean delete(Context context, Theme theme) {
        if (theme == null) return false;
        return deleteByKey(context, theme.key);
    }

    public static boolean deleteByKey(Context context, String key) {
        try {
            JSONArray array = read(context);
            JSONArray kept = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                if (!key.equals(object.optString("key"))) kept.put(object);
            }
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                    .edit().putString(KEY, kept.toString()).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static JSONArray read(Context context) {
        String raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null);
        if (raw == null || raw.trim().isEmpty()) return new JSONArray();
        try {
            return new JSONArray(raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static Theme themeFrom(JSONObject object) {
        try {
            String key = object.optString("key");
            String name = object.optString("name", "Custom");
            return Theme.of(key, name,
                    object.optInt("bgTop"), object.optInt("bgBottom"),
                    object.optInt("cardTop"), object.optInt("cardBottom"), object.optInt("cardStroke"),
                    object.optInt("accent"), object.optInt("accentSoft"), object.optInt("onAccent"),
                    object.optInt("text"), object.optInt("textDim"));
        } catch (Exception e) {
            return null;
        }
    }

    private static void write(Context context, List<JSONObject> entries) {
        JSONArray array = new JSONArray();
        for (JSONObject entry : entries) array.put(entry);
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putString(KEY, array.toString()).apply();
    }

    // ------------------------------------------------------------ derivation

    private static float luminance(int color) {
        double r = ((color >> 16) & 0xFF) / 255.0;
        double g = ((color >> 8) & 0xFF) / 255.0;
        double b = (color & 0xFF) / 255.0;
        return (float) (0.2126 * r + 0.7152 * g + 0.0722 * b);
    }

    private static int mix(int a, int b, float amountOfB) {
        float keep = 1f - amountOfB;
        int r = Math.round((((a >> 16) & 0xFF) * keep) + (((b >> 16) & 0xFF) * amountOfB));
        int g = Math.round((((a >> 8) & 0xFF) * keep) + (((b >> 8) & 0xFF) * amountOfB));
        int bl = Math.round(((a & 0xFF) * keep) + ((b & 0xFF) * amountOfB));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static Theme derive(String key, String name, int bgTop, int bgBottom, int accent) {
        int cardTop = mix(bgTop, 0xFFFFFFFF, 0.16f);
        int cardBottom = mix(bgTop, 0xFFFFFFFF, 0.24f);
        int cardStroke = withAlpha(accent, 0x38);
        int accentSoft = mix(accent, 0xFFFFFFFF, 0.42f);
        int onAccent = luminance(accent) > 0.55f ? 0xFF0D1622 : 0xFFF7FAFF;
        int text = luminance(bgTop) > 0.5f ? 0xFF141D29 : 0xFFF2F6FB;
        int textDim = mix(text, bgTop, 0.55f);
        return Theme.of(key, name, bgTop, bgBottom, cardTop, cardBottom, cardStroke,
                accent, accentSoft, onAccent, text, textDim);
    }

    /** Creates and stores a new custom theme. Returns the saved theme. */
    public static Theme create(Context context, String name, int bgTop, int bgBottom, int accent) {
        List<Theme> existing = list(context);
        String key = "custom:c" + (existing.size() + 1);
        int suffix = 2;
        // avoid key clashes after deletions
        while (findByKey(existing, key) != null) {
            key = "custom:c" + (existing.size() + suffix);
            suffix++;
        }
        Theme theme = derive(key, name == null || name.trim().isEmpty() ? "Custom theme" : name.trim(),
                bgTop, bgBottom, accent);

        List<JSONObject> entries = new ArrayList<>();
        try {
            JSONArray raw = read(context);
            for (int i = 0; i < raw.length(); i++) {
                if (raw.optJSONObject(i) != null) entries.add(raw.optJSONObject(i));
            }
            JSONObject object = new JSONObject();
            object.put("key", theme.key);
            object.put("name", theme.label);
            object.put("bgTop", theme.bgTop);
            object.put("bgBottom", theme.bgBottom);
            object.put("cardTop", theme.cardTop);
            object.put("cardBottom", theme.cardBottom);
            object.put("cardStroke", theme.cardStroke);
            object.put("accent", theme.accent);
            object.put("accentSoft", theme.accentSoft);
            object.put("onAccent", theme.onAccent);
            object.put("text", theme.text);
            object.put("textDim", theme.textDim);
            entries.add(object);
            write(context, entries);
        } catch (Exception ignored) {
        }
        return theme;
    }

    private static Theme findByKey(List<Theme> themes, String key) {
        for (Theme theme : themes) {
            if (key.equals(theme.key)) return theme;
        }
        return null;
    }

    // ---------------------------------------------------------- picker dialog

    private static final int[][] BACKGROUND_PAIRS = {
            {0xFF061423, 0xFF0B3049},   // sky
            {0xFF0C0A1C, 0xFF1A1440},   // midnight
            {0xFF221008, 0xFF3D1A12},   // sunset
            {0xFF04170F, 0xFF0A2E20},   // emerald
            {0xFF12202E, 0xFF1C3040},   // slate
            {0xFF140A14, 0xFF241026}    // plum
    };

    private static final int[] ACCENTS = {
            0xFF4FC3F7, 0xFF8E7BFF, 0xFFFF8A5B, 0xFF34D399, 0xFFFCD34D,
            0xFFF472B6, 0xFF22D3EE, 0xFFA3E635, 0xFFF87171, 0xFFE879F9
    };

    /** Shows the maker dialog. Called after the user taps "New theme". */
    public static void showMakerDialog(final Activity activity, final Runnable onSaved) {
        final int[] bgIndex = {0};
        final int[] accentIndex = {0};
        final View[] selected = {null};
        final View[] selectedAccentWrap = {null};

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpInt(20), dpInt(8), dpInt(20), dpInt(6));

        sectionLabel(root, "BACKGROUND");
        LinearLayout bgRow = new LinearLayout(activity);
        bgRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < BACKGROUND_PAIRS.length; i++) {
            final int index = i;
            final FrameLayout wrap = new FrameLayout(activity);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpInt(46), dpInt(32));
            params.rightMargin = dpInt(8);
            wrap.setLayoutParams(params);
            wrap.setBackground(frame(false, 0x66FFFFFF));
            View inner = new View(activity);
            FrameLayout.LayoutParams innerParams = new FrameLayout.LayoutParams(
                    dpInt(38), dpInt(24), Gravity.CENTER);
            inner.setLayoutParams(innerParams);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{BACKGROUND_PAIRS[index][0], BACKGROUND_PAIRS[index][1]});
            bg.setCornerRadius(Ui.dp(6));
            inner.setBackground(bg);
            wrap.addView(inner);
            wrap.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bgIndex[0] = index;
                    if (selected[0] != null) selected[0].setBackground(frame(false, 0x66FFFFFF));
                    v.setBackground(frame(true, 0xFFFFFFFF));
                    selected[0] = v;
                }
            });
            bgRow.addView(wrap);
        }
        root.addView(bgRow);

        sectionLabel(root, "ACCENT");
        LinearLayout accentRow = new LinearLayout(activity);
        accentRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < ACCENTS.length; i++) {
            final int index = i;
            final FrameLayout wrap = new FrameLayout(activity);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpInt(38), dpInt(38));
            params.rightMargin = dpInt(8);
            wrap.setLayoutParams(params);
            wrap.setBackground(frame(false, 0x66FFFFFF));
            View inner = new View(activity);
            FrameLayout.LayoutParams innerParams = new FrameLayout.LayoutParams(
                    dpInt(22), dpInt(22), Gravity.CENTER);
            inner.setLayoutParams(innerParams);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(ACCENTS[index]);
            inner.setBackground(dot);
            wrap.addView(inner);
            wrap.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    accentIndex[0] = index;
                    if (selectedAccentWrap[0] != null) {
                        selectedAccentWrap[0].setBackground(frame(false, 0x66FFFFFF));
                    }
                    v.setBackground(frame(true, 0xFFFFFFFF));
                    selectedAccentWrap[0] = v;
                }
            });
            accentRow.addView(wrap);
        }
        root.addView(accentRow);

        new AlertDialog.Builder(activity)
                .setTitle("New theme")
                .setView(root)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int[] pair = BACKGROUND_PAIRS[bgIndex[0]];
                        Theme created = create(activity,
                                "Custom " + (list(activity).size() + 1),
                                pair[0], pair[1], ACCENTS[accentIndex[0]]);
                        Theme.select(activity, created);
                        if (onSaved != null) onSaved.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void sectionLabel(LinearLayout parent, String text) {
        TextView label = new TextView(parent.getContext());
        label.setText(text);
        label.setTextColor(0xFF8CAEC6);
        label.setTextSize(10);
        label.setLetterSpacing(0.22f);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dpInt(14);
        params.bottomMargin = dpInt(6);
        label.setLayoutParams(params);
        parent.addView(label);
    }

    private static GradientDrawable frame(boolean selected, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(Ui.dp(10));
        d.setColor(0x00000000);
        d.setStroke(selected ? dpInt(2) : dpInt(1), stroke);
        return d;
    }

    private static int dpInt(float value) {
        return Math.round(Ui.dp(value));
    }
}
