package com.pulse.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;

/** Loads embedded album art as a bitmap, for the notification and lock screen. */
public final class Artwork {

    private Artwork() {
    }

    /** Loads embedded album art as a bitmap, for the notification and lock screen.
     *  Checks the user's cover override first, then the embedded ID3 artwork. */
    public static Bitmap load(Context context, Song song, int targetPx) {
        if (song == null) return null;
        Bitmap override = ArtworkStore.load(context, song.id, targetPx);
        if (override != null) return override;
        if (song.path == null) return null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(song.path);
            byte[] data = retriever.getEmbeddedPicture();
            if (data == null || data.length == 0) return null;

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx);
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static int sampleSize(int width, int height, int target) {
        if (width <= 0 || height <= 0 || target <= 0) return 1;
        int size = 1;
        while ((width / (size * 2)) >= target && (height / (size * 2)) >= target) {
            size *= 2;
        }
        return size;
    }
}
