package com.pulse.player;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single SQLite store for the user's data: favourites, hidden naats,
 * rename overrides, playlists and recent playback.
 *
 * On first open the legacy SharedPreferences / JSON stores are imported
 * once. If the import fails, the caller keeps reading the legacy stores,
 * so nothing is lost.
 */
public final class NaatDb extends SQLiteOpenHelper {

    private static final String FILE = "naat.db";
    private static final int VERSION = 1;
    private static final String KEY_MIGRATED = "legacy_migrated";

    private static final Object LOCK = new Object();
    private static NaatDb instance;

    private final Context app;
    private volatile boolean migrationFailed;

    private NaatDb(Context context) {
        super(context.getApplicationContext(), FILE, null, VERSION);
        this.app = context.getApplicationContext();
    }

    public static NaatDb get(Context context) {
        synchronized (LOCK) {
            if (instance == null) {
                instance = new NaatDb(context);
                try {
                    instance.migrateLegacy();
                } catch (Throwable ignored) {
                    instance.migrationFailed = true;
                }
            }
            return instance;
        }
    }

    public boolean isMigrated() {
        return !migrationFailed;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        createTables(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        createTables(db);
    }

    private static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS favorites(song_id INTEGER PRIMARY KEY)");
        db.execSQL("CREATE TABLE IF NOT EXISTS hidden_songs(song_id INTEGER PRIMARY KEY)");
        db.execSQL("CREATE TABLE IF NOT EXISTS hidden_folders(folder TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE IF NOT EXISTS renamed_titles(song_id INTEGER PRIMARY KEY, title TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS playlists(name TEXT PRIMARY KEY, created_at INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS playlist_songs("
                + " playlist_name TEXT NOT NULL,"
                + " song_id INTEGER NOT NULL,"
                + " PRIMARY KEY(playlist_name, song_id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS recent("
                + " recent_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " song_id INTEGER UNIQUE,"
                + " played_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT)");
    }

    private void migrateLegacy() {
        SQLiteDatabase db = getWritableDatabase();
        createTables(db);
        if (isMetaSet(db)) return;

        db.beginTransaction();
        try {
            // favourites + hidden ids from pulse_prefs
            importIdSet(db, "favourite_songs", "favorites");
            importIdSet(db, "hidden_songs", "hidden_songs");
            importStringSet(db, "hidden_folders", "hidden_folders", "folder");

            // rename overrides (JSON object under pulse_prefs)
            String renamedJson = app.getSharedPreferences("pulse_prefs", Context.MODE_PRIVATE)
                    .getString("renamed_titles", null);
            if (renamedJson != null && !renamedJson.isEmpty()) {
                try {
                    JSONObject map = new JSONObject(renamedJson);
                    java.util.Iterator<String> keys = map.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        String title = map.optString(key, null);
                        if (title == null) continue;
                        try {
                            long id = Long.parseLong(key);
                            db.execSQL("INSERT OR IGNORE INTO renamed_titles(song_id, title) VALUES(?,?)",
                                    new Object[]{id, title});
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            // playlists.json + usage.json — parsed inline so the migration never
            // depends on the DB-backed implementations that will replace them.
            for (java.util.Map.Entry<String, List<Long>> entry
                    : readLegacyPlaylists().entrySet()) {
                db.execSQL("INSERT OR IGNORE INTO playlists(name, created_at) VALUES(?,?)",
                        new Object[]{entry.getKey(), System.currentTimeMillis()});
                for (Long id : entry.getValue()) {
                    db.execSQL("INSERT OR IGNORE INTO playlist_songs(playlist_name, song_id) VALUES(?,?)",
                            new Object[]{entry.getKey(), id});
                }
            }
            List<Long> legacyRecent = readLegacyRecent();
            long playedAt = System.currentTimeMillis();
            for (int i = legacyRecent.size() - 1; i >= 0; i--) {
                db.execSQL("INSERT OR IGNORE INTO recent(song_id, played_at) VALUES(?,?)",
                        new Object[]{legacyRecent.get(i), playedAt - (long) i * 1000L});
            }

            db.execSQL("INSERT OR REPLACE INTO meta(key, value) VALUES(?, ?)",
                    new Object[]{KEY_MIGRATED, "1"});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private boolean isMetaSet(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT value FROM meta WHERE key=?",
                new String[]{KEY_MIGRATED});
        try {
            return cursor.moveToFirst() && "1".equals(cursor.getString(0));
        } finally {
            cursor.close();
        }
    }

    private void importIdSet(SQLiteDatabase db, String prefsKey, String table) {
        Set<String> values = app.getSharedPreferences("pulse_prefs", Context.MODE_PRIVATE)
                .getStringSet(prefsKey, new HashSet<String>());
        for (String value : values) {
            try {
                db.execSQL("INSERT OR IGNORE INTO " + table + "(song_id) VALUES(?)",
                        new Object[]{Long.parseLong(value)});
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void importStringSet(SQLiteDatabase db, String prefsKey, String table, String column) {
        Set<String> values = app.getSharedPreferences("pulse_prefs", Context.MODE_PRIVATE)
                .getStringSet(prefsKey, new HashSet<String>());
        for (String value : values) {
            if (value == null || value.isEmpty()) continue;
            db.execSQL("INSERT OR IGNORE INTO " + table + "(" + column + ") VALUES(?)",
                    new Object[]{value});
        }
    }

    private java.util.LinkedHashMap<String, List<Long>> readLegacyPlaylists() {
        java.util.LinkedHashMap<String, List<Long>> result = new java.util.LinkedHashMap<>();
        String raw = readSmallFile(new java.io.File(app.getFilesDir(), "playlists.json"));
        if (raw == null) return result;
        try {
            org.json.JSONArray array = new org.json.JSONObject(raw).optJSONArray("playlists");
            if (array == null) return result;
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String name = object.optString("name", "");
                if (name.isEmpty()) continue;
                List<Long> ids = new ArrayList<>();
                org.json.JSONArray idArray = object.optJSONArray("ids");
                if (idArray != null) {
                    for (int j = 0; j < idArray.length(); j++) ids.add(idArray.optLong(j));
                }
                result.put(name, ids);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private List<Long> readLegacyRecent() {
        List<Long> result = new ArrayList<>();
        String raw = readSmallFile(new java.io.File(app.getFilesDir(), "usage.json"));
        if (raw == null) return result;
        try {
            org.json.JSONArray array = new org.json.JSONObject(raw).optJSONArray("recent");
            if (array == null) return result;
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject object = array.optJSONObject(i);
                if (object != null) result.add(object.optLong("id"));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static String readSmallFile(java.io.File file) {
        try {
            if (!file.isFile() || file.length() > 512 * 1024) return null;
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(file), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ----------------------------------------------------------------- helpers

    private static List<Long> ids(SQLiteDatabase db, String table) {
        List<Long> result = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT song_id FROM " + table + " ORDER BY song_id", null);
        try {
            while (cursor.moveToNext()) result.add(cursor.getLong(0));
        } finally {
            cursor.close();
        }
        return result;
    }

    private static Set<String> idStrings(SQLiteDatabase db, String table) {
        List<Long> values = ids(db, table);
        Set<String> result = new HashSet<>();
        for (Long id : values) result.add(String.valueOf(id));
        return result;
    }

    private static Set<String> strings(SQLiteDatabase db, String table, String column) {
        Set<String> result = new HashSet<>();
        Cursor cursor = db.rawQuery("SELECT " + column + " FROM " + table, null);
        try {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        } finally {
            cursor.close();
        }
        return result;
    }

    private static boolean contains(SQLiteDatabase db, String table, long id) {
        Cursor cursor = db.rawQuery("SELECT 1 FROM " + table + " WHERE song_id=?",
                new String[]{String.valueOf(id)});
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    public synchronized boolean isFavorite(long id) {
        return contains(getReadableDatabase(), "favorites", id);
    }

    public synchronized Set<String> favoriteIds() {
        return idStrings(getReadableDatabase(), "favorites");
    }

    public synchronized boolean toggleFavorite(long id) {
        SQLiteDatabase db = getWritableDatabase();
        if (contains(db, "favorites", id)) {
            db.delete("favorites", "song_id=?", new String[]{String.valueOf(id)});
            return false;
        }
        db.execSQL("INSERT OR IGNORE INTO favorites(song_id) VALUES(?)", new Object[]{id});
        return true;
    }

    public synchronized void setFavorite(long id, boolean favorite) {
        SQLiteDatabase db = getWritableDatabase();
        if (favorite) {
            db.execSQL("INSERT OR IGNORE INTO favorites(song_id) VALUES(?)", new Object[]{id});
        } else {
            db.delete("favorites", "song_id=?", new String[]{String.valueOf(id)});
        }
    }

    public synchronized void replaceFavorites(Set<String> values) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("favorites", null, null);
            for (String value : values) {
                try {
                    db.execSQL("INSERT OR IGNORE INTO favorites(song_id) VALUES(?)",
                            new Object[]{Long.parseLong(value)});
                } catch (NumberFormatException ignored) {
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ---- hidden naats / folders ----

    public synchronized boolean isSongHidden(long id) {
        return contains(getReadableDatabase(), "hidden_songs", id);
    }

    public synchronized void setSongHidden(long id, boolean hidden) {
        SQLiteDatabase db = getWritableDatabase();
        if (hidden) {
            db.execSQL("INSERT OR IGNORE INTO hidden_songs(song_id) VALUES(?)", new Object[]{id});
        } else {
            db.delete("hidden_songs", "song_id=?", new String[]{String.valueOf(id)});
        }
    }

    public synchronized Set<String> hiddenSongs() {
        return idStrings(getReadableDatabase(), "hidden_songs");
    }

    public synchronized void replaceHiddenSongs(Set<String> values) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("hidden_songs", null, null);
            for (String value : values) {
                try {
                    db.execSQL("INSERT OR IGNORE INTO hidden_songs(song_id) VALUES(?)",
                            new Object[]{Long.parseLong(value)});
                } catch (NumberFormatException ignored) {
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized boolean isFolderHidden(String folder) {
        if (folder == null) return false;
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT 1 FROM hidden_folders WHERE folder=?", new String[]{folder});
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    public synchronized void setFolderHidden(String folder, boolean hidden) {
        if (folder == null) return;
        SQLiteDatabase db = getWritableDatabase();
        if (hidden) {
            db.execSQL("INSERT OR IGNORE INTO hidden_folders(folder) VALUES(?)",
                    new Object[]{folder});
        } else {
            db.delete("hidden_folders", "folder=?", new String[]{folder});
        }
    }

    public synchronized Set<String> hiddenFolders() {
        return strings(getReadableDatabase(), "hidden_folders", "folder");
    }

    public synchronized void replaceHiddenFolders(Set<String> values) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("hidden_folders", null, null);
            for (String value : values) {
                db.execSQL("INSERT OR IGNORE INTO hidden_folders(folder) VALUES(?)",
                        new Object[]{value});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ---- rename overrides ----

    public synchronized String renamedTitle(long id) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT title FROM renamed_titles WHERE song_id=?",
                new String[]{String.valueOf(id)});
        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    public synchronized void setRenamedTitle(long id, String title) {
        SQLiteDatabase db = getWritableDatabase();
        if (title == null || title.trim().isEmpty()) {
            db.delete("renamed_titles", "song_id=?", new String[]{String.valueOf(id)});
            return;
        }
        db.execSQL("INSERT OR REPLACE INTO renamed_titles(song_id, title) VALUES(?,?)",
                new Object[]{id, title});
    }

    // ---- playlists ----

    public synchronized List<Playlists.Playlist> allPlaylists() {
        List<Playlists.Playlist> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT name FROM playlists ORDER BY rowid", null);
        try {
            while (cursor.moveToNext()) {
                Playlists.Playlist playlist = new Playlists.Playlist(cursor.getString(0));
                loadPlaylistSongs(db, playlist);
                result.add(playlist);
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    private static void loadPlaylistSongs(SQLiteDatabase db, Playlists.Playlist playlist) {
        Cursor cursor = db.rawQuery(
                "SELECT song_id FROM playlist_songs WHERE playlist_name=?",
                new String[]{playlist.name});
        try {
            while (cursor.moveToNext()) playlist.ids.add(cursor.getLong(0));
        } finally {
            cursor.close();
        }
    }

    public synchronized boolean createPlaylist(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String clean = name.trim();
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT 1 FROM playlists WHERE name=?",
                new String[]{clean});
        try {
            if (cursor.moveToFirst()) return false;
        } finally {
            cursor.close();
        }
        db.execSQL("INSERT INTO playlists(name, created_at) VALUES(?,?)",
                new Object[]{clean, System.currentTimeMillis()});
        return true;
    }

    public synchronized void deletePlaylist(String name) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("playlist_songs", "playlist_name=?", new String[]{name});
        db.delete("playlists", "name=?", new String[]{name});
    }

    public synchronized boolean renamePlaylist(String from, String to) {
        if (to == null || to.trim().isEmpty()) return false;
        String clean = to.trim();
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT 1 FROM playlists WHERE name=?",
                new String[]{clean});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        if (exists && !clean.equals(from)) return false;

        db.beginTransaction();
        try {
            db.execSQL("UPDATE playlists SET name=? WHERE name=?", new Object[]{clean, from});
            db.execSQL("UPDATE playlist_songs SET playlist_name=? WHERE playlist_name=?",
                    new Object[]{clean, from});
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public synchronized boolean addToPlaylist(String name, long songId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Cursor cursor = db.rawQuery(
                    "SELECT 1 FROM playlist_songs WHERE playlist_name=? AND song_id=?",
                    new String[]{name, String.valueOf(songId)});
            boolean already = cursor.moveToFirst();
            cursor.close();
            if (already) return false;
            db.execSQL("INSERT INTO playlist_songs(playlist_name, song_id) VALUES(?,?)",
                    new Object[]{name, songId});
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void removeFromPlaylist(String name, long songId) {
        getWritableDatabase().delete("playlist_songs",
                "playlist_name=? AND song_id=?",
                new String[]{name, String.valueOf(songId)});
    }

    public synchronized void replacePlaylists(List<Playlists.Playlist> list) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("playlist_songs", null, null);
            db.delete("playlists", null, null);
            long createdAt = System.currentTimeMillis();
            for (Playlists.Playlist playlist : list) {
                db.execSQL("INSERT INTO playlists(name, created_at) VALUES(?,?)",
                        new Object[]{playlist.name, createdAt});
                for (Long id : playlist.ids) {
                    db.execSQL("INSERT OR IGNORE INTO playlist_songs(playlist_name, song_id) VALUES(?,?)",
                            new Object[]{playlist.name, id});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ---- recent playback ----

    public synchronized void markPlayed(long songId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("recent", "song_id=?", new String[]{String.valueOf(songId)});
            db.execSQL("INSERT INTO recent(song_id, played_at) VALUES(?,?)",
                    new Object[]{songId, System.currentTimeMillis()});
            db.execSQL("DELETE FROM recent WHERE recent_id NOT IN "
                            + "(SELECT recent_id FROM recent ORDER BY played_at DESC LIMIT 300)");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized List<Long> recent() {
        List<Long> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT song_id FROM recent ORDER BY played_at DESC LIMIT 300", null);
        try {
            while (cursor.moveToNext()) result.add(cursor.getLong(0));
        } finally {
            cursor.close();
        }
        return result;
    }

    public synchronized void replaceRecent(List<Long> ids) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("recent", null, null);
            long playedAt = System.currentTimeMillis();
            for (int i = ids.size() - 1; i >= 0; i--) {
                db.execSQL("INSERT INTO recent(song_id, played_at) VALUES(?,?)",
                        new Object[]{ids.get(i), playedAt - (long) i * 1000L});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void clearRecent() {
        getWritableDatabase().delete("recent", null, null);
    }
}
