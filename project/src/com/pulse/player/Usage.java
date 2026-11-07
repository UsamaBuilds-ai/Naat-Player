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

/** Recently played songs and play counters, kept in a small JSON file. */
public final class Usage {

    private static final String FILE = "usage.json";
    private static final int LIMIT = 300;
    private static final Object LOCK = new Object();

    private Usage() {
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), FILE);
    }

    public static void markPlayed(Context context, long songId) {
        if (context == null) return;
        synchronized (LOCK) {
            List<Long> ids = read(context);
            ids.remove(songId);
            ids.add(0, songId);
            while (ids.size() > LIMIT) ids.remove(ids.size() - 1);
            write(context, ids);
        }
    }

    public static List<Long> recent(Context context) {
        synchronized (LOCK) {
            return read(context);
        }
    }

    public static void clear(Context context) {
        synchronized (LOCK) {
            write(context, new ArrayList<Long>());
        }
    }

    static void replaceAll(Context context, List<Long> ids) {
        synchronized (LOCK) {
            List<Long> copy = new ArrayList<>(ids);
            while (copy.size() > LIMIT) copy.remove(copy.size() - 1);
            write(context, copy);
        }
    }

    private static List<Long> read(Context context) {
        List<Long> ids = new ArrayList<>();
        File file = file(context);
        if (!file.exists()) return ids;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONObject root = new JSONObject(sb.toString());
            JSONArray array = root.optJSONArray("recent");
            if (array == null) return ids;
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) ids.add(object.optLong("id"));
            }
        } catch (Exception ignored) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
        return ids;
    }

    private static void write(Context context, List<Long> ids) {
        OutputStreamWriter writer = null;
        try {
            JSONArray array = new JSONArray();
            for (Long id : ids) {
                JSONObject object = new JSONObject();
                object.put("id", id.longValue());
                array.put(object);
            }
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("recent", array);

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
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored2) {
                }
            }
        }
    }
}
