package com.pulse.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * User-picked cover art, stored per song in the app's private files directory.
 * MediaStore does not let apps write embedded ID3 artwork portably, so the
 * app keeps its own override and uses it everywhere artwork is shown.
 */
public final class ArtworkStore {

    private static final int MAX_SOURCE = 1024;

    private ArtworkStore() {
    }

    public static File fileFor(Context context, long songId) {
        File dir = new File(context.getFilesDir(), "art");
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        return new File(dir, songId + ".jpg");
    }

    public static boolean hasOverride(Context context, long songId) {
        File file = fileFor(context, songId);
        return file != null && file.isFile();
    }

    public static boolean clear(Context context, long songId) {
        File file = fileFor(context, songId);
        return file == null || !file.exists() || file.delete();
    }

    /**
     * Copies the picked image into private storage.
     * @return true on success
     */
    public static boolean setOverride(Context context, long songId, Uri uri) {
        if (uri == null) return false;
        File file = fileFor(context, songId);
        if (file == null) return false;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) return false;
            BitmapFactory.decodeStream(in, null, bounds);
            in.close();

            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SOURCE) {
                sample *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            in = context.getContentResolver().openInputStream(uri);
            if (in == null) return false;
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
            in.close();
            if (bitmap == null) return false;

            FileOutputStream out = new FileOutputStream(file);
            try {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out);
                out.flush();
            } finally {
                out.close();
            }
            bitmap.recycle();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Decodes the override, downsampled to roughly {@code targetPx}. */
    public static Bitmap load(Context context, long songId, int targetPx) {
        File file = fileFor(context, songId);
        if (file == null || !file.isFile()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Exception e) {
            return null;
        }
    }
}
