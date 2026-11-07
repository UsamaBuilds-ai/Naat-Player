package com.pulse.player;

import android.content.Context;
import android.content.SharedPreferences;

/** Colour palettes. Every screen paints itself from the active palette at runtime. */
public class Theme {

    public final String key;
    public final String label;
    public final int bgTop, bgBottom;
    public final int cardTop, cardBottom, cardStroke;
    public final int accent, accentSoft, onAccent;
    public final int text, textDim;

    private Theme(String key, String label,
                  int bgTop, int bgBottom,
                  int cardTop, int cardBottom, int cardStroke,
                  int accent, int accentSoft, int onAccent,
                  int text, int textDim) {
        this.key = key;
        this.label = label;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.cardTop = cardTop;
        this.cardBottom = cardBottom;
        this.cardStroke = cardStroke;
        this.accent = accent;
        this.accentSoft = accentSoft;
        this.onAccent = onAccent;
        this.text = text;
        this.textDim = textDim;
    }

    public static final Theme SKY = new Theme("sky", "Sky blue",
            0xFF061423, 0xFF0B3049,
            0xFF0E2438, 0xFF15334A, 0x264FC3F7,
            0xFF4FC3F7, 0xFFA5E3FF, 0xFF04202F,
            0xFFEAF6FF, 0xFF8CAEC6);

    public static final Theme MIDNIGHT = new Theme("midnight", "Midnight violet",
            0xFF0C0A1C, 0xFF1A1440,
            0xFF16122E, 0xFF211A47, 0x268E7BFF,
            0xFF8E7BFF, 0xFFC7BBFF, 0xFF120C2B,
            0xFFF3F0FF, 0xFF9C93BD);

    public static final Theme SUNSET = new Theme("sunset", "Sunset",
            0xFF221008, 0xFF3D1A12,
            0xFF2C1512, 0xFF3A1D18, 0x26FF8A5B,
            0xFFFF8A5B, 0xFFFFC2A5, 0xFF2A0F06,
            0xFFFFF4EE, 0xFFC79A88);

    public static final Theme EMERALD = new Theme("emerald", "Emerald",
            0xFF04170F, 0xFF0A2E20,
            0xFF0B251A, 0xFF123325, 0x2634D399,
            0xFF34D399, 0xFFA7F3D0, 0xFF032318,
            0xFFEAFBF3, 0xFF8DB6A6);

    public static final Theme[] ALL = {SKY, MIDNIGHT, SUNSET, EMERALD};

    private static final String PREFS = "pulse_prefs";
    private static final String KEY_THEME = "theme_key";

    public static Theme current(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = prefs.getString(KEY_THEME, SKY.key);
        for (Theme theme : ALL) {
            if (theme.key.equals(key)) return theme;
        }
        return SKY;
    }

    public static void select(Context context, Theme theme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME, theme.key).apply();
    }
}
