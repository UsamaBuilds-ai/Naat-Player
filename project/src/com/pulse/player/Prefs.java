package com.pulse.player;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Local preferences: hidden songs/folders, sort order and playback toggles. */
public final class Prefs {

    private static final String FILE = "pulse_prefs";
    private static final String KEY_HIDDEN_SONGS = "hidden_songs";
    private static final String KEY_HIDDEN_FOLDERS = "hidden_folders";
    private static final String KEY_FAVORITES = "favourite_songs";
    private static final String KEY_SHOW_HIDDEN = "show_hidden";
    private static final String KEY_SORT_MODE = "sort_mode";
    private static final String KEY_SORT_DESC = "sort_desc";
    private static final String KEY_PAUSE_DETACH = "pause_detach";
    private static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    private static final String KEY_SHOW_HIDDEN_FILES = "show_hidden_files";
    private static final String KEY_EQ_ENABLED = "eq_enabled";
    private static final String KEY_EQ_PRESET = "eq_preset";
    private static final String KEY_EQ_BANDS = "eq_bands";
    private static final String KEY_RENAMED_TITLES = "renamed_titles";
    private static final String KEY_LYRICS_LANG = "lyrics_language";
    private static final String KEY_LYRICS_ONLINE = "lyrics_online";
    private static final String KEY_LYRICS_TRANSLATE = "lyrics_translate";

    public static final int SORT_NAME = 0;
    public static final int SORT_DATE = 1;
    public static final int SORT_DURATION = 2;
    public static final int SORT_ALBUM = 3;

    private Prefs() {
    }

    private static SharedPreferences sp(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static Set<String> getSet(Context context, String key) {
        return new HashSet<>(sp(context).getStringSet(key, new HashSet<String>()));
    }

    // ------------------------------------------------------------- hidden items

    public static boolean isSongHidden(Context context, long id) {
        return getSet(context, KEY_HIDDEN_SONGS).contains(String.valueOf(id));
    }

    public static void setSongHidden(Context context, long id, boolean hidden) {
        Set<String> set = getSet(context, KEY_HIDDEN_SONGS);
        if (hidden) set.add(String.valueOf(id));
        else set.remove(String.valueOf(id));
        sp(context).edit().putStringSet(KEY_HIDDEN_SONGS, set).apply();
    }

    public static Set<String> hiddenSongs(Context context) {
        return getSet(context, KEY_HIDDEN_SONGS);
    }

    public static boolean isFolderHidden(Context context, String folder) {
        return folder != null && getSet(context, KEY_HIDDEN_FOLDERS).contains(folder);
    }

    public static void setFolderHidden(Context context, String folder, boolean hidden) {
        if (folder == null) return;
        Set<String> set = getSet(context, KEY_HIDDEN_FOLDERS);
        if (hidden) set.add(folder);
        else set.remove(folder);
        sp(context).edit().putStringSet(KEY_HIDDEN_FOLDERS, set).apply();
    }

    public static Set<String> hiddenFolders(Context context) {
        return getSet(context, KEY_HIDDEN_FOLDERS);
    }

    // --------------------------------------------------------------- favourites

    public static boolean isFavorite(Context context, long id) {
        return getSet(context, KEY_FAVORITES).contains(String.valueOf(id));
    }

    /** Returns true when the song is a favourite after the toggle. */
    public static boolean toggleFavorite(Context context, long id) {
        Set<String> set = getSet(context, KEY_FAVORITES);
        String key = String.valueOf(id);
        boolean added = set.add(key);
        if (!added) set.remove(key);
        sp(context).edit().putStringSet(KEY_FAVORITES, set).apply();
        return added;
    }

    public static void setFavorite(Context context, long id, boolean favorite) {
        Set<String> set = getSet(context, KEY_FAVORITES);
        if (favorite) set.add(String.valueOf(id));
        else set.remove(String.valueOf(id));
        sp(context).edit().putStringSet(KEY_FAVORITES, set).apply();
    }

    public static Set<String> favoriteIds(Context context) {
        return getSet(context, KEY_FAVORITES);
    }

    public static void replaceFavorites(Context context, Set<String> values) {
        sp(context).edit().putStringSet(KEY_FAVORITES, new HashSet<>(values)).apply();
    }

    public static void replaceHiddenSongs(Context context, Set<String> values) {
        sp(context).edit().putStringSet(KEY_HIDDEN_SONGS, new HashSet<>(values)).apply();
    }

    public static void replaceHiddenFolders(Context context, Set<String> values) {
        sp(context).edit().putStringSet(KEY_HIDDEN_FOLDERS, new HashSet<>(values)).apply();
    }

    /** When true the library also lists hidden songs and hidden folders. */
    public static boolean showHidden(Context context) {
        return sp(context).getBoolean(KEY_SHOW_HIDDEN, false);
    }

    public static void setShowHidden(Context context, boolean value) {
        sp(context).edit().putBoolean(KEY_SHOW_HIDDEN, value).apply();
    }

    /** Whether the folder browser lists dot-folders as well. */
    public static boolean showHiddenFiles(Context context) {
        return sp(context).getBoolean(KEY_SHOW_HIDDEN_FILES, false);
    }

    public static void setShowHiddenFiles(Context context, boolean value) {
        sp(context).edit().putBoolean(KEY_SHOW_HIDDEN_FILES, value).apply();
    }

    // ------------------------------------------------------------------- sort

    public static int sortMode(Context context) {
        return sp(context).getInt(KEY_SORT_MODE, SORT_NAME);
    }

    public static boolean sortDescending(Context context) {
        return sp(context).getBoolean(KEY_SORT_DESC, false);
    }

    public static void setSort(Context context, int mode, boolean descending) {
        sp(context).edit().putInt(KEY_SORT_MODE, mode)
                .putBoolean(KEY_SORT_DESC, descending).apply();
    }

    public static String sortLabel(Context context) {
        String[] names = {"Naat name", "Date added", "Duration", "Album"};
        int mode = sortMode(context);
        if (mode < 0 || mode >= names.length) mode = 0;
        return names[mode] + (sortDescending(context) ? " ↓" : " ↑");
    }

    // --------------------------------------------------------------- playback

    public static boolean pauseWhenDetached(Context context) {
        return sp(context).getBoolean(KEY_PAUSE_DETACH, true);
    }

    public static void setPauseWhenDetached(Context context, boolean value) {
        sp(context).edit().putBoolean(KEY_PAUSE_DETACH, value).apply();
    }

    public static boolean keepScreenOn(Context context) {
        return sp(context).getBoolean(KEY_KEEP_SCREEN_ON, false);
    }

    public static void setKeepScreenOn(Context context, boolean value) {
        sp(context).edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply();
    }

    // --------------------------------------------------------------- equalizer

    public static boolean equalizerEnabled(Context context) {
        return sp(context).getBoolean(KEY_EQ_ENABLED, true);
    }

    public static void setEqualizerEnabled(Context context, boolean value) {
        sp(context).edit().putBoolean(KEY_EQ_ENABLED, value).apply();
    }

    public static int equalizerPreset(Context context) {
        return sp(context).getInt(KEY_EQ_PRESET, 0);
    }

    public static void setEqualizerPreset(Context context, int value) {
        sp(context).edit().putInt(KEY_EQ_PRESET, value).apply();
    }

    public static short[] equalizerBands(Context context) {
        String raw = sp(context).getString(KEY_EQ_BANDS, null);
        if (raw == null || raw.isEmpty()) return null;
        String[] parts = raw.split(",");
        short[] bands = new short[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                bands[i] = Short.parseShort(parts[i].trim());
            } catch (NumberFormatException ignored) {
                bands[i] = 0;
            }
        }
        return bands;
    }

    public static void setEqualizerBands(Context context, short[] bands) {
        if (bands == null) {
            sp(context).edit().remove(KEY_EQ_BANDS).apply();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bands.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(bands[i]);
        }
        sp(context).edit().putString(KEY_EQ_BANDS, sb.toString()).apply();
    }

    /**
     * Local nicknames set by the user ("Auto name", manual rename). MediaStore
     * lets the TITLE revert to the ID3 tag on the next media scan, so the app
     * keeps its own map and applies it at every load.
     */
    public static String renamedTitle(Context context, long id) {
        org.json.JSONObject map = readRenamedTitles(context);
        if (map == null) return null;
        String value = map.optString(String.valueOf(id), null);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    public static void setRenamedTitle(Context context, long id, String title) {
        org.json.JSONObject map = readRenamedTitles(context);
        if (map == null) map = new org.json.JSONObject();
        try {
            if (title == null || title.trim().isEmpty()) {
                map.remove(String.valueOf(id));
            } else {
                map.put(String.valueOf(id), title);
            }
            sp(context).edit().putString(KEY_RENAMED_TITLES, map.toString()).apply();
        } catch (org.json.JSONException ignored) {
        }
    }

    public static void clearRenamedTitle(Context context, long id) {
        setRenamedTitle(context, id, null);
    }

    private static org.json.JSONObject readRenamedTitles(Context context) {
        String raw = sp(context).getString(KEY_RENAMED_TITLES, null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return new org.json.JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    // ----------------------------------------------------------------- lyrics

    /** Language code the user wants lyrics in. Urdu is the default. */
    public static String lyricsLanguage(Context context) {
        return sp(context).getString(KEY_LYRICS_LANG, "ur");
    }

    public static void setLyricsLanguage(Context context, String code) {
        sp(context).edit().putString(KEY_LYRICS_LANG, code).apply();
    }

    public static String lyricsLanguageLabel(Context context) {
        String code = lyricsLanguage(context);
        for (int i = 0; i < LyricsOnline.LANG_CODES.length; i++) {
            if (LyricsOnline.LANG_CODES[i].equals(code)) return LyricsOnline.LANG_LABELS[i];
        }
        return LyricsOnline.LANG_LABELS[0];
    }

    /** Look lyrics up online when nothing local is available. */
    public static boolean lyricsOnline(Context context) {
        return sp(context).getBoolean(KEY_LYRICS_ONLINE, true);
    }

    public static void setLyricsOnline(Context context, boolean value) {
        sp(context).edit().putBoolean(KEY_LYRICS_ONLINE, value).apply();
    }

    /** Machine translate to Urdu when only another language was found. */
    public static boolean translateToUrdu(Context context) {
        return sp(context).getBoolean(KEY_LYRICS_TRANSLATE, true);
    }

    public static void setTranslateToUrdu(Context context, boolean value) {
        sp(context).edit().putBoolean(KEY_LYRICS_TRANSLATE, value).apply();
    }

    /** Set when settings change something the library screen has to re-read. */
    public static void markLibraryDirty(Context context) {
        sp(context).edit().putBoolean("library_dirty", true).apply();
    }

    public static boolean consumeLibraryDirty(Context context) {
        boolean dirty = sp(context).getBoolean("library_dirty", false);
        if (dirty) sp(context).edit().putBoolean("library_dirty", false).apply();
        return dirty;
    }
}
