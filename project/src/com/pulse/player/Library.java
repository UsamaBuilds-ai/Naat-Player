package com.pulse.player;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Reads the device audio library and applies the user's sort order. */
public final class Library {

    private Library() {
    }

    public static List<Song> load(Context context) {
        List<Song> songs = new ArrayList<>();
        try {
            ContentResolver resolver = context.getContentResolver();
            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.DATA
            };
            Cursor cursor = resolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    MediaStore.Audio.Media.IS_MUSIC + "!=0",
                    null,
                    null);

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String path = cursor.getString(7);
                    if (path == null) continue;
                    String title = cursor.getString(2);
                    songs.add(new Song(
                            cursor.getLong(0), cursor.getLong(1),
                            text(title, fileName(path)),
                            text(cursor.getString(3), ""),
                            text(cursor.getString(4), ""),
                            cursor.getLong(5),
                            cursor.getLong(6) * 1000L,
                            path));
                }
                cursor.close();
            }
        } catch (Exception ignored) {
        }
        sort(context, songs);
        return songs;
    }

    public static void sort(Context context, List<Song> songs) {
        final int mode = Prefs.sortMode(context);
        final boolean desc = Prefs.sortDescending(context);

        Comparator<Song> comparator;
        switch (mode) {
            case Prefs.SORT_DATE:
                comparator = new Comparator<Song>() {
                    @Override
                    public int compare(Song a, Song b) {
                        return Long.compare(a.dateAdded, b.dateAdded);
                    }
                };
                break;
            case Prefs.SORT_DURATION:
                comparator = new Comparator<Song>() {
                    @Override
                    public int compare(Song a, Song b) {
                        return Long.compare(a.durationMs, b.durationMs);
                    }
                };
                break;
            case Prefs.SORT_ALBUM:
                comparator = new Comparator<Song>() {
                    @Override
                    public int compare(Song a, Song b) {
                        int byAlbum = a.album.compareToIgnoreCase(b.album);
                        return byAlbum != 0 ? byAlbum : a.title.compareToIgnoreCase(b.title);
                    }
                };
                break;
            default:
                comparator = new Comparator<Song>() {
                    @Override
                    public int compare(Song a, Song b) {
                        return a.title.compareToIgnoreCase(b.title);
                    }
                };
                break;
        }

        if (desc) {
            final Comparator<Song> base = comparator;
            comparator = new Comparator<Song>() {
                @Override
                public int compare(Song a, Song b) {
                    return base.compare(b, a);
                }
            };
        }
        Collections.sort(songs, comparator);
    }

    private static String text(String value, String fallback) {
        if (value == null || value.trim().isEmpty()
                || "<unknown>".equalsIgnoreCase(value.trim())) return fallback;
        return value;
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
