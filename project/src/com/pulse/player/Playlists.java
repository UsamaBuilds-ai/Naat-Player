package com.pulse.player;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/** User playlists. Stored in SQLite; the legacy playlists.json import is once-only. */
public final class Playlists {

    public static class Playlist {
        public String name;
        public final List<Long> ids = new ArrayList<>();

        public Playlist(String name) {
            this.name = name;
        }
    }

    private static final String FILE = "playlists.json";
    private static final Object LOCK = new Object();

    private Playlists() {
    }

    private static boolean migrated(Context context) {
        try {
            return NaatDb.get(context).isMigrated();
        } catch (Throwable t) {
            return false;
        }
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), FILE);
    }

    public static List<Playlist> all(Context context) {
        synchronized (LOCK) {
            if (migrated(context)) return NaatDb.get(context).allPlaylists();
            return read(context);
        }
    }

    public static Playlist find(Context context, String name) {
        synchronized (LOCK) {
            for (Playlist playlist : all(context)) {
                if (playlist.name.equals(name)) return playlist;
            }
            return null;
        }
    }

    public static boolean create(Context context, String name) {
        synchronized (LOCK) {
            if (name == null || name.trim().isEmpty()) return false;
            String clean = name.trim();
            if (migrated(context)) return NaatDb.get(context).createPlaylist(clean);
            List<Playlist> list = read(context);
            for (Playlist playlist : list) {
                if (playlist.name.equalsIgnoreCase(clean)) return false;
            }
            list.add(new Playlist(clean));
            write(context, list);
            return true;
        }
    }

    public static void delete(Context context, String name) {
        synchronized (LOCK) {
            if (migrated(context)) {
                NaatDb.get(context).deletePlaylist(name);
                return;
            }
            List<Playlist> list = read(context);
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).name.equals(name)) list.remove(i);
            }
            write(context, list);
        }
    }

    public static boolean rename(Context context, String from, String to) {
        synchronized (LOCK) {
            if (to == null || to.trim().isEmpty()) return false;
            if (migrated(context)) {
                return NaatDb.get(context).renamePlaylist(from, to.trim());
            }
            String clean = to.trim();
            List<Playlist> list = read(context);
            for (Playlist playlist : list) {
                if (playlist.name.equalsIgnoreCase(clean) && !playlist.name.equals(from)) {
                    return false;
                }
            }
            for (Playlist playlist : list) {
                if (playlist.name.equals(from)) {
                    playlist.name = clean;
                    write(context, list);
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean addSong(Context context, String name, long songId) {
        synchronized (LOCK) {
            if (migrated(context)) return NaatDb.get(context).addToPlaylist(name, songId);
            List<Playlist> list = read(context);
            for (Playlist playlist : list) {
                if (!playlist.name.equals(name)) continue;
                if (playlist.ids.contains(songId)) return false;
                playlist.ids.add(songId);
                write(context, list);
                return true;
            }
            return false;
        }
    }

    public static void removeSong(Context context, String name, long songId) {
        synchronized (LOCK) {
            if (migrated(context)) {
                NaatDb.get(context).removeFromPlaylist(name, songId);
                return;
            }
            List<Playlist> list = read(context);
            for (Playlist playlist : list) {
                if (!playlist.name.equals(name)) continue;
                playlist.ids.remove(songId);
                write(context, list);
                return;
            }
        }
    }

    static void replaceAll(Context context, List<Playlist> list) {
        synchronized (LOCK) {
            if (migrated(context)) {
                NaatDb.get(context).replacePlaylists(list);
                return;
            }
            write(context, list);
        }
    }

    // ------------------------------------------------------------- file access

    private static List<Playlist> read(Context context) {
        List<Playlist> result = new ArrayList<>();
        File file = file(context);
        if (!file.exists()) return result;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JSONObject root = new JSONObject(sb.toString());
            JSONArray array = root.optJSONArray("playlists");
            if (array == null) return result;

            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                Playlist playlist = new Playlist(object.optString("name"));
                JSONArray ids = object.optJSONArray("ids");
                if (ids != null) {
                    for (int j = 0; j < ids.length(); j++) {
                        playlist.ids.add(ids.optLong(j));
                    }
                }
                if (!playlist.name.isEmpty()) result.add(playlist);
            }
        } catch (Exception ignored) {
        } finally {
            close(reader);
        }
        return result;
    }

    private static void write(Context context, List<Playlist> list) {
        OutputStreamWriter writer = null;
        try {
            JSONArray array = new JSONArray();
            for (Playlist playlist : list) {
                JSONObject object = new JSONObject();
                object.put("name", playlist.name);
                JSONArray ids = new JSONArray();
                for (Long id : playlist.ids) ids.put(id.longValue());
                object.put("ids", ids);
                array.put(object);
            }
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("playlists", array);

            File file = file(context);
            File temp = new File(file.getParentFile(), FILE + ".tmp");
            writer = new OutputStreamWriter(new FileOutputStream(temp), "UTF-8");
            writer.write(root.toString());
            writer.flush();
            writer.close();
            writer = null;
            if (!temp.renameTo(file)) {
                OutputStreamWriter direct = new OutputStreamWriter(
                        new FileOutputStream(file), "UTF-8");
                direct.write(root.toString());
                direct.close();
            }
        } catch (Exception ignored) {
            close(writer);
        }
    }

    private static void close(java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
