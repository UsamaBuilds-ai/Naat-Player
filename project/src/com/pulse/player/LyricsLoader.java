package com.pulse.player;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lyrics lookup order:
 *
 * <ol>
 *   <li>language specific sidecar, e.g. {@code naat.ur.lrc}</li>
 *   <li>plain sidecar, e.g. {@code naat.lrc}</li>
 *   <li>ID3 USLT frame embedded in the audio file</li>
 *   <li>previously cached online result</li>
 *   <li>online lookup (LRCLIB, then lyrics.ovh) when enabled</li>
 * </ol>
 */
public class LyricsLoader {

    public static class Line {
        public final long timeMs;
        public final String text;

        Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    public static class Lyrics {
        public final List<Line> lines;
        public final boolean synced;
        /** Human readable origin: local, embedded, cached, online, translated. */
        public String source = "none";

        Lyrics(List<Line> lines, boolean synced) {
            this.lines = lines;
            this.synced = synced;
        }
    }

    private static final Pattern TIME_TAG =
            Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");

    private static final long MAX_CACHE_BYTES = 512 * 1024;

    public static Lyrics load(Context context, Song song) {
        if (song == null || song.path == null) return null;

        String lang = Prefs.lyricsLanguage(context);

        if (!"auto".equals(lang)) {
            Lyrics localized = parse(readSidecar(song.path, lang));
            if (localized != null) {
                localized.source = "local · " + lang;
                return localized;
            }
        }

        Lyrics plain = parse(readSidecar(song.path, null));
        if (plain != null) {
            plain.source = "local";
            return plain;
        }

        Lyrics embedded = parse(readEmbeddedUslt(song.path));
        if (embedded != null) {
            embedded.source = "embedded";
            return embedded;
        }

        File cached = cacheFile(context, song, lang);
        Lyrics fromCache = parse(readTextFile(cached));
        if (fromCache != null) {
            fromCache.source = "cached";
            return fromCache;
        }

        if (Prefs.lyricsOnline(context) && hasInternet(context)) {
            LyricsOnline.Result result = LyricsOnline.fetch(context, song, lang);
            if (result != null) {
                Lyrics online = parse(result.text);
                if (online != null) {
                    online.source = result.translated ? "online · translated" : "online";
                    writeCache(cached, result.text);
                    return online;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------- sidecar .lrc

    private static String readSidecar(String audioPath, String lang) {
        String base = audioPath;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);

        if (lang != null && !lang.isEmpty()) {
            String[] localized = {
                    base + "." + lang + ".lrc",
                    base + "." + lang + ".LRC",
                    base + "." + lang.toUpperCase() + ".lrc",
                    base + "_" + lang + ".lrc"
            };
            for (String candidate : localized) {
                String text = readTextFile(new File(candidate));
                if (text != null) return text;
            }
        }

        String[] candidates = {base + ".lrc", base + ".LRC", base + ".Lrc"};
        for (String candidate : candidates) {
            String text = readTextFile(new File(candidate));
            if (text != null) return text;
        }
        return null;
    }

    private static String readTextFile(File file) {
        if (file == null) return null;
        try {
            if (!file.isFile() || file.length() > MAX_CACHE_BYTES) return null;
            byte[] data = new byte[(int) file.length()];
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            raf.readFully(data);
            raf.close();
            String text = new String(data, "UTF-8");
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            return text;
        } catch (Exception ignored) {
            return null;
        }
    }

    // -------------------------------------------------------------- ID3 USLT

    private static String readEmbeddedUslt(String audioPath) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(audioPath, "r");
            byte[] header = new byte[10];
            raf.readFully(header);
            if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') return null;

            int version = header[3] & 0xFF;
            int size = ((header[6] & 0x7F) << 21) | ((header[7] & 0x7F) << 14)
                    | ((header[8] & 0x7F) << 7) | (header[9] & 0x7F);
            int limit = Math.min(size, 4 * 1024 * 1024);
            if (version < 3) return null;

            byte[] body = new byte[limit];
            raf.seek(10);
            raf.readFully(body);

            int pos = 0;
            while (pos + 10 < limit) {
                String id = new String(body, pos, 4, "ISO-8859-1");
                if (id.charAt(0) == '\u0000') break;
                int frameSize = version == 4
                        ? ((body[pos + 4] & 0x7F) << 21) | ((body[pos + 5] & 0x7F) << 14)
                          | ((body[pos + 6] & 0x7F) << 7) | (body[pos + 7] & 0x7F)
                        : ((body[pos + 4] & 0xFF) << 24) | ((body[pos + 5] & 0xFF) << 16)
                          | ((body[pos + 6] & 0xFF) << 8) | (body[pos + 7] & 0xFF);
                if (frameSize <= 0 || pos + 10 + frameSize > limit) break;

                if ("USLT".equals(id) || "ULT".equals(id)) {
                    int start = pos + 10;
                    int encoding = body[start] & 0xFF;
                    int textStart = start + 4;
                    int end = start + frameSize;
                    return decodeUslt(body, textStart, end, encoding);
                }
                pos += 10 + frameSize;
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (raf != null) raf.close();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String decodeUslt(byte[] body, int start, int end, int encoding) {
        try {
            String charSet;
            switch (encoding) {
                case 0:
                    charSet = "ISO-8859-1";
                    break;
                case 1:
                    charSet = "UTF-16";
                    break;
                case 2:
                    charSet = "UTF-16BE";
                    break;
                default:
                    charSet = "UTF-8";
                    break;
            }

            // skip the content descriptor (null terminated) to reach the lyrics
            int i = start;
            while (i < end) {
                if (encoding == 1 || encoding == 2) {
                    if (i + 1 < end && body[i] == 0 && body[i + 1] == 0) {
                        i += 2;
                        break;
                    }
                    i += 2;
                } else {
                    if (body[i] == 0) {
                        i += 1;
                        break;
                    }
                    i += 1;
                }
            }
            int textFrom = i;
            if (textFrom >= end) return null;

            String text = new String(body, textFrom, end - textFrom, charSet);
            int nul = text.indexOf('\u0000');
            if (nul >= 0) text = text.substring(0, nul);
            text = text.trim();
            return text.isEmpty() ? null : text;
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ cache

    private static File cacheFile(Context context, Song song, String lang) {
        try {
            File dir = new File(context.getFilesDir(), "lyrics");
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            return new File(dir, song.id + "-" + lang + ".lrc");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeCache(File file, String text) {
        if (file == null || text == null || text.trim().isEmpty()) return;
        if (text.length() > MAX_CACHE_BYTES) return;
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(text.getBytes("UTF-8"));
        } catch (Exception ignored) {
        } finally {
            try {
                if (out != null) out.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Clears cached online lyrics so the next open fetches again. */
    public static void clearCache(Context context) {
        try {
            File dir = new File(context.getFilesDir(), "lyrics");
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File file : files) file.delete();
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasInternet(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return true;
            NetworkInfo info = manager.getActiveNetworkInfo();
            return info == null || info.isConnected();
        } catch (Throwable ignored) {
            return true;
        }
    }

    // ------------------------------------------------------------------ parsing

    private static Lyrics parse(String raw) {
        if (raw == null) return null;
        String[] rows = raw.replace("\r", "").split("\n");
        List<Line> lines = new ArrayList<>();
        boolean synced = false;

        for (String row : rows) {
            String trimmed = row.trim();
            if (trimmed.isEmpty()) continue;
            Matcher m = TIME_TAG.matcher(trimmed);
            boolean matched = false;
            while (m.find()) {
                matched = true;
                synced = true;
                long minutes = Long.parseLong(m.group(1));
                long seconds = Long.parseLong(m.group(2));
                long fraction = 0;
                String rawFraction = m.group(3);
                if (rawFraction != null) {
                    fraction = Long.parseLong(rawFraction);
                    if (rawFraction.length() == 2) fraction *= 10;
                    if (rawFraction.length() == 1) fraction *= 100;
                }
                String text = trimmed.substring(m.end()).trim();
                lines.add(new Line(minutes * 60000 + seconds * 1000 + fraction, text));
            }
            if (!matched) lines.add(new Line(-1, trimmed));
        }

        if (lines.isEmpty()) return null;

        if (synced) {
            Collections.sort(lines, new Comparator<Line>() {
                @Override
                public int compare(Line a, Line b) {
                    return Long.compare(a.timeMs, b.timeMs);
                }
            });
        }
        if (lines.size() > 3000) lines = lines.subList(0, 3000);
        return new Lyrics(lines, synced);
    }
}
