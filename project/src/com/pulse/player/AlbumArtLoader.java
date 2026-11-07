package com.pulse.player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads embedded album art off the UI thread and keeps it in an LRU cache.
 * Falls back to the sky-blue gradient tile when a track has no artwork.
 */
public class AlbumArtLoader {

    private final LruCache<Long, Bitmap> cache = new LruCache<Long, Bitmap>(60) {
        @Override
        protected int sizeOf(Long key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    public void load(final Song song, final int targetPx,
                     final ImageView image, final TextView glyph) {
        Bitmap cached = cache.get(song.id);
        if (cached != null) {
            image.setImageBitmap(cached);
            glyph.setVisibility(TextView.GONE);
            return;
        }

        image.setImageDrawable(null);
        glyph.setVisibility(TextView.VISIBLE);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = readArt(song, targetPx);
                if (bitmap == null) return;
                cache.put(song.id, bitmap);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Object tag = image.getTag();
                        if (tag instanceof Long && ((Long) tag) == song.id) {
                            image.setImageBitmap(bitmap);
                            glyph.setVisibility(TextView.GONE);
                        }
                    }
                });
            }
        });
    }

    private Bitmap readArt(Song song, int targetPx) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(song.path);
            byte[] embedded = retriever.getEmbeddedPicture();
            if (embedded == null || embedded.length == 0) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(embedded, 0, embedded.length, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(embedded, 0, embedded.length, options);
        } catch (Throwable ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
            }
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
