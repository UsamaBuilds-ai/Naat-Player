package com.pulse.player;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Local preferences: hidden songs/folders, sort order and playback toggles.
 *  Favorites / hidden items / rename overrides live in SQLite via {@link NaatDb},
 *  with a one-time import of the legacy stores. */
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
    private static final String KEY_CROSSFADE = "crossfade";
    private static final String KEY_RECENT_SEARCHES = "recent_searches";

    public static final int SORT_NAME = 0;
    public static final int SORT_DATE = 1;
    public static final int SORT_DURATION = 2;
    public static final int SORT_ALBUM = 3;

    private Prefs() {
    }

    private static SharedPreferences sp(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static boolean migrated(Context context) {
        try {
            return NaatDb.get(context).isMigrated();
        } catch (Throwable t) {
            return false;
        }
    }

    private static Set<String> legacySet(Context context, String key) {
        return new HashSet<>(sp(context).getStringSet(key, new HashSet<String>()));
    }

    private static void legacySet(Context context, String key, Set<String> values) {
        sp(context).edit().putStringSet(key, new HashSet<>(values)).apply();
    }

    // ------------------------------------------------------------- hidden items

    public static boolean isSongHidden(Context context, long id) {
        if (migrated(context)) return NaatDb.get(context).isSongHidden(id);
        return legacySet(context, KEY_HIDDEN_SONGS).contains(String.valueOf(id));
    }

    public static void setSongHidden(Context context, long id, boolean hidden) {
        if (migrated(context)) {
            NaatDb.get(context).setSongHidden(id, hidden);
            return;
        }
        Set<String> set = legacySet(context, KEY_HIDDEN_SONGS);
        if (hidden) set.add(String.valueOf(id));
        else set.remove(String.valueOf(id));
        legacySet(context, KEY_HIDDEN_SONGS, set);
    }

    public static Set<String> hiddenSongs(Context context) {
        if (migrated(context)) return NaatDb.get(context).hiddenSongs();
        return legacySet(context, KEY_HIDDEN_SONGS);
    }

    public static boolean isFolderHidden(Context context, String folder) {
        if (migrated(context)) return NaatDb.get(context).isFolderHidden(folder);
        return folder != null && legacySet(context, KEY_HIDDEN_FOLDERS).contains(folder);
    }

    public static void setFolderHidden(Context context, String folder, boolean hidden) {
        if (folder == null) return;
        if (migrated(context)) {
            NaatDb.get(context).setFolderHidden(folder, hidden);
            return;
        }
        Set<String> set = legacySet(context, KEY_HIDDEN_FOLDERS);
        if (hidden) set.add(folder);
        else set.remove(folder);
        legacySet(context, KEY_HIDDEN_FOLDERS, set);
    }

    public static Set<String> hiddenFolders(Context context) {
        if (migrated(context)) return NaatDb.get(context).hiddenFolders();
        return legacySet(context, KEY_HIDDEN_FOLDERS);
    }

    // --------------------------------------------------------------- favourites

    public static boolean isFavorite(Context context, long id) {
        if (migrated(context)) return NaatDb.get(context).isFavorite(id);
        return legacySet(context, KEY_FAVORITES).contains(String.valueOf(id));
    }

    /** Returns true when the song is a favourite after the toggle. */
    public static boolean toggleFavorite(Context context, long id) {
        if (migrated(context)) return NaatDb.get(context).toggleFavorite(id);
        Set<String> set = legacySet(context, KEY_FAVORITES);
        boolean added = set.add(String.valueOf(id));
        if (!added) set.remove(String.valueOf(id));
        legacySet(context, KEY_FAVORITES, set);
        return added;
    }

    public static void setFavorite(Context context, long id, boolean favorite) {
        if (migrated(context)) {
            NaatDb.get(context).setFavorite(id, favorite);
            return;
        }
        Set<String> set = legacySet(context, KEY_FAVORITES);
        if (favorite) set.add(String.valueOf(id));
        else set.remove(String.valueOf(id));
        legacySet(context, KEY_FAVORITES, set);
    }

    public static Set<String> favoriteIds(Context context) {
        if (migrated(context)) return NaatDb.get(context).favoriteIds();
        return legacySet(context, KEY_FAVORITES);
    }

    public static void replaceFavorites(Context context, Set<String> values) {
        if (migrated(context)) {
            NaatDb.get(context).replaceFavorites(values);
            return;
        }
        legacySet(context, KEY_FAVORITES, new HashSet<>(values));
    }

    public static void replaceHiddenSongs(Context context, Set<String> values) {
        if (migrated(context)) {
            NaatDb.get(context).replaceHiddenSongs(values);
            return;
        }
        legacySet(context, KEY_HIDDEN_SONGS, new HashSet<>(values));
    }

    public static void replaceHiddenFolders(Context context, Set<String> values) {
        if (migrated(context)) {
            NaatDb.get(context).replaceHiddenFolders(values);
            return;
        }
        legacySet(context, KEY_HIDDEN_FOLDERS, new HashSet<>(values));
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

    /** Crossfade between tracks (fade out the ending naat, fade in the next). */
    public static boolean crossfade(Context context) {
        return sp(context).getBoolean(KEY_CROSSFADE, true);
    }

    public static void setCrossfade(Context context, boolean value) {
        sp(context).edit().putBoolean(KEY_CROSSFADE, value).apply();
    }

    // ------------------------------------------------------- recent searches

    /** Most recent distinct searches, newest first. */
    public static java.util.List<String> recentSearches(Context context) {
        java.util.List<String> result = new java.util.ArrayList<>();
        String raw = sp(context).getString(KEY_RECENT_SEARCHES, null);
        if (raw == null || raw.trim().isEmpty()) return result;
        for (String entry : raw.split("\n")) {
            String clean = entry.trim();
            if (!clean.isEmpty() && !result.contains(clean)) result.add(clean);
        }
        return result;
    }

    public static void addRecentSearch(Context context, String query) {
        if (query == null) return;
        String clean = query.trim();
        if (clean.length() < 2) return;
        java.util.List<String> list = recentSearches(context);
        list.remove(clean);
        list.add(0, clean);
        while (list.size() > 8) list.remove(list.size() - 1);
        sp(context).edit().putString(KEY_RECENT_SEARCHES, joinLines(list)).apply();
    }

    public static void clearRecentSearches(Context context) {
        sp(context).edit().remove(KEY_RECENT_SEARCHES).apply();
    }

    private static String joinLines(java.util.List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(list.get(i).replace('\n', ' '));
        }
        return sb.toString();
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

    /**
     * Local nicknames set by the user ("Auto name", manual rename). MediaStore
     * lets the TITLE revert to the ID3 tag on the next media scan, so the app
     * keeps its own map and applies it at every load.
     */
    public static String renamedTitle(Context context, long id) {
        if (migrated(context)) {
            String nick = NaatDb.get(context).renamedTitle(id);
            if (nick != null) return nick;
        }
        String raw = sp(context).getString(KEY_RENAMED_TITLES, null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            org.json.JSONObject map = new org.json.JSONObject(raw);
            String value = map.optString(String.valueOf(id), null);
            return value == null || value.trim().isEmpty() ? null : value;
        } catch (Exception e) {
            return null;
        }
    }

    public static void setRenamedTitle(Context context, long id, String title) {
        if (migrated(context)) {
            NaatDb.get(context).setRenamedTitle(id, title);
            return;
        }
        org.json.JSONObject map;
        String raw = sp(context).getString(KEY_RENAMED_TITLES, null);
        try {
            map = raw == null || raw.isEmpty()
                    ? new org.json.JSONObject() : new org.json.JSONObject(raw);
            if (title == null || title.trim().isEmpty()) {
                map.remove(String.valueOf(id));
            } else {
                map.put(String.valueOf(id), title);
            }
            sp(context).edit().putString(KEY_RENAMED_TITLES, map.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void clearRenamedTitle(Context context, long id) {
        setRenamedTitle(context, id, null);
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
