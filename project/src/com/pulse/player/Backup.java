package com.pulse.player;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** JSON backup of favourites, hidden items, playlists and recently played songs. */
public final class Backup {

    private Backup() {
    }

    public static String export(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "Naat Player");
        root.put("version", 1);

        root.put("favourites", toArray(Prefs.favoriteIds(context)));
        root.put("hiddenSongs", toArray(Prefs.hiddenSongs(context)));
        root.put("hiddenFolders", toArray(Prefs.hiddenFolders(context)));
        root.put("recent", toArray(Usage.recent(context)));

        JSONArray playlists = new JSONArray();
        for (Playlists.Playlist playlist : Playlists.all(context)) {
            JSONObject object = new JSONObject();
            object.put("name", playlist.name);
            JSONArray ids = new JSONArray();
            for (Long id : playlist.ids) ids.put(id.longValue());
            object.put("ids", ids);
            playlists.put(object);
        }
        root.put("playlists", playlists);
        return root.toString(2);
    }

    public static void restore(Context context, String json) throws Exception {
        JSONObject root = new JSONObject(json);

        JSONArray favourites = root.optJSONArray("favourites");
        if (favourites != null) {
            java.util.Set<String> set = new java.util.HashSet<>();
            for (int i = 0; i < favourites.length(); i++) {
                set.add(String.valueOf(favourites.optLong(i)));
            }
            Prefs.replaceFavorites(context, set);
        }

        JSONArray hiddenSongs = root.optJSONArray("hiddenSongs");
        if (hiddenSongs != null) {
            java.util.Set<String> set = new java.util.HashSet<>();
            for (int i = 0; i < hiddenSongs.length(); i++) {
                set.add(String.valueOf(hiddenSongs.optLong(i)));
            }
            Prefs.replaceHiddenSongs(context, set);
        }

        JSONArray hiddenFolders = root.optJSONArray("hiddenFolders");
        if (hiddenFolders != null) {
            java.util.Set<String> set = new java.util.HashSet<>();
            for (int i = 0; i < hiddenFolders.length(); i++) {
                set.add(hiddenFolders.optString(i));
            }
            Prefs.replaceHiddenFolders(context, set);
        }

        JSONArray recent = root.optJSONArray("recent");
        if (recent != null) {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < recent.length(); i++) ids.add(recent.optLong(i));
            Usage.replaceAll(context, ids);
        }

        JSONArray playlists = root.optJSONArray("playlists");
        if (playlists != null) {
            List<Playlists.Playlist> list = new ArrayList<>();
            for (int i = 0; i < playlists.length(); i++) {
                JSONObject object = playlists.optJSONObject(i);
                if (object == null) continue;
                Playlists.Playlist playlist =
                        new Playlists.Playlist(object.optString("name"));
                JSONArray ids = object.optJSONArray("ids");
                if (ids != null) {
                    for (int j = 0; j < ids.length(); j++) {
                        playlist.ids.add(ids.optLong(j));
                    }
                }
                if (!playlist.name.isEmpty()) list.add(playlist);
            }
            Playlists.replaceAll(context, list);
        }

        Prefs.markLibraryDirty(context);
    }

    private static JSONArray toArray(Set<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) array.put(value);
        return array;
    }

    private static JSONArray toArray(List<Long> values) {
        JSONArray array = new JSONArray();
        for (Long value : values) array.put(value.longValue());
        return array;
    }
}
