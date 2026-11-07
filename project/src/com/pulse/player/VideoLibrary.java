package com.pulse.player;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

public final class VideoLibrary {

    private VideoLibrary() {
    }

    public static List<VideoItem> load(Context context) {
        List<VideoItem> videos = new ArrayList<>();
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION
        };
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC");
        if (cursor == null) return videos;
        try {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String title = cursor.getString(nameColumn);
                videos.add(new VideoItem(id, title == null ? "Video" : title,
                        cursor.getLong(durationColumn),
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)));
            }
        } finally {
            cursor.close();
        }
        return videos;
    }
}
