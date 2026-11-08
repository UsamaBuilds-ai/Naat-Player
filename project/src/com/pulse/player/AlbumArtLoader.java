package com.pulse.player;

import android.content.Context;
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
 * Loads embedded album art off the UI thread and keeps decoded bitmaps in an
 * LRU cache.
 *
 * The cache is keyed by song id <em>and</em> requested size, so a 40dp list
 * thumbnail can never be reused for the big Now Playing disc (that was the
 * reason the large artwork looked blurry).
 */
public class AlbumArtLoader {

    /** 32 MB of decoded bitmaps - enough for a large disc plus a screen of thumbs. */
    private static final int CACHE_KB = 32 * 1024;

    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(CACHE_KB) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Context appContext;

    public AlbumArtLoader(Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    private static String key(long id, int targetPx) {
        int bucket = targetPx <= 0 ? 64 : Integer.highestOneBit(targetPx);
        return id + "@" + bucket;
    }

    public void load(final Song song, final int targetPx,
                     final ImageView image, final TextView glyph) {
        if (song == null) return;
        final String cacheKey = key(song.id, targetPx);
        Bitmap cached = cache.get(cacheKey);
        if (cached != null) {
            image.setImageBitmap(cached);
            glyph.setVisibility(TextView.GONE);
            return;
        }

        image.setTag(song.id);
        image.setImageDrawable(null);
        glyph.setVisibility(TextView.VISIBLE);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = readArt(song, targetPx);
                if (bitmap == null) return;
                cache.put(cacheKey, bitmap);
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

    /** Loads the artwork straight into the spinning disc on the Now Playing screen. */
    public void load(final Song song, final int targetPx, final DiscArtView disc) {
        if (song == null || disc == null) return;
        final String cacheKey = key(song.id, targetPx);
        Bitmap cached = cache.get(cacheKey);
        if (cached != null) {
            disc.setArt(cached);
            return;
        }

        disc.setTag(song.id);
        disc.setArt(null);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = readArt(song, targetPx);
                if (bitmap == null) return;
                cache.put(cacheKey, bitmap);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Object tag = disc.getTag();
                        if (tag instanceof Long && ((Long) tag) == song.id) {
                            disc.setArt(bitmap);
                        }
                    }
                });
            }
        });
    }

    private Bitmap readArt(Song song, int targetPx) {
        if (appContext != null) {
            Bitmap override = ArtworkStore.load(appContext, song.id, targetPx);
            if (override != null) return override;
        }
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
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
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
