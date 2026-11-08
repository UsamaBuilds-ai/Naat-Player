package com.pulse.player;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Online lyrics lookup.
 *
 * Primary source is LRCLIB (https://lrclib.net), an open lyrics database whose
 * server is MIT licensed (https://github.com/tranxuanthang/lrclib). It needs no
 * API key and has no rate limiting. lyrics.ovh is used as a fallback.
 *
 * When the user picked Urdu and the fetched text is in another script, the text
 * is machine translated through the free MyMemory API. Anonymous MyMemory use is
 * limited to roughly 5000 characters per day, so translation is best effort and
 * every request is capped; whatever succeeds is cached by {@link LyricsLoader}.
 */
public final class LyricsOnline {

    public static final String[] LANG_CODES = {
            "ur", "hi", "en", "pa", "sd", "ps", "ar", "auto"
    };
    public static final String[] LANG_LABELS = {
            "Urdu", "Hindi", "English", "Punjabi", "Sindhi", "Pashto", "Arabic", "Auto"
    };

    private static final String USER_AGENT = "NaatPlayer/2.0 (Android)";
    private static final int MAX_TRANSLATE_REQUESTS = 22;
    private static final int CHUNK_CHARS = 420;

    private LyricsOnline() {
    }

    public static class Result {
        public final String text;
        public final boolean translated;

        Result(String text, boolean translated) {
            this.text = text;
            this.translated = translated;
        }
    }

    /** Returns null when nothing usable could be fetched. */
    public static Result fetch(Context context, Song song, String lang) {
        if (song == null) return null;

        String text = fromLrclib(song, lang);
        if (text == null) text = fromLyricsOvh(song);
        if (text == null || text.trim().isEmpty()) return null;

        if (!scriptMatches(text, lang) && "ur".equals(lang)
                && Prefs.translateToUrdu(context)) {
            String translated = translate(text, detectScriptLang(text), "ur");
            if (translated != null && !translated.trim().isEmpty()) {
                return new Result(translated, true);
            }
        }
        return new Result(text, false);
    }

    // ----------------------------------------------------------------- LRCLIB

    private static String fromLrclib(Song song, String lang) {
        String text = lrclibQuery(song.title, song.artist, lang);
        if (text == null) text = lrclibQuery(song.title, null, lang);
        return text;
    }

    private static String lrclibQuery(String track, String artist, String lang) {
        try {
            StringBuilder url = new StringBuilder("https://lrclib.net/api/search?track_name=")
                    .append(enc(track));
            if (artist != null && !artist.trim().isEmpty()) {
                url.append("&artist_name=").append(enc(artist));
            }
            String body = get(url.toString());
            if (body == null) return null;
            JSONArray results = new JSONArray(body);
            String fallback = null;
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null) continue;
                String synced = item.optString("syncedLyrics", "");
                String plain = item.optString("plainLyrics", "");
                String candidate = synced != null && synced.trim().length() > 0 ? synced : plain;
                if (candidate == null || candidate.trim().isEmpty()) continue;
                if (fallback == null) fallback = candidate;
                if (scriptMatches(candidate, lang)) return candidate;
            }
            return fallback;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------- lyrics.ovh

    private static String fromLyricsOvh(Song song) {
        try {
            String artist = song.artist == null || song.artist.trim().isEmpty()
                    ? "unknown" : song.artist.trim();
            String url = "https://api.lyrics.ovh/v1/" + enc(artist) + "/" + enc(song.title);
            String body = get(url);
            if (body == null) return null;
            JSONObject json = new JSONObject(body);
            String lyrics = json.optString("lyrics", "");
            return lyrics.trim().isEmpty() ? null : lyrics;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------- translate

    /**
     * Translates plain text line block by line block. Timestamps from an LRC
     * source are dropped because machine translation would scramble them.
     */
    private static String translate(String text, String from, String to) {
        if (from.equalsIgnoreCase(to)) return text;

        String plain = text.replaceAll("(?m)^(\\[[^\\]]*\\])+\\s*", "");
        String[] lines = plain.replace("\r", "").split("\n");

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (current.length() > 0 && current.length() + line.length() > CHUNK_CHARS) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (chunks.size() >= MAX_TRANSLATE_REQUESTS) break;
            current.append(line).append('\n');
        }
        if (current.length() > 0 && chunks.size() < MAX_TRANSLATE_REQUESTS) {
            chunks.add(current.toString());
        }
        if (chunks.isEmpty()) return null;

        StringBuilder out = new StringBuilder();
        int succeeded = 0;
        for (String chunk : chunks) {
            String translated = translateChunk(chunk, from, to);
            if (translated != null) {
                out.append(translated);
                succeeded++;
            } else {
                out.append(chunk);
            }
        }
        return succeeded == 0 ? null : out.toString();
    }

    private static String translateChunk(String chunk, String from, String to) {
        try {
            String url = "https://api.mymemory.translated.net/get?q=" + enc(chunk)
                    + "&langpair=" + enc(from) + "|" + enc(to);
            String body = get(url);
            if (body == null) return null;
            JSONObject json = new JSONObject(body);
            JSONObject data = json.optJSONObject("responseData");
            if (data == null) return null;
            String translated = data.optString("translatedText", "");
            return translated.trim().isEmpty() ? null : translated;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------- script test

    /** True when most letters of {@code text} belong to the script of {@code lang}. */
    public static boolean scriptMatches(String text, String lang) {
        if (text == null || text.trim().isEmpty()) return false;
        if (lang == null || "auto".equals(lang)) return true;

        int arabic = 0;
        int devanagari = 0;
        int gurmukhi = 0;
        int latin = 0;
        int letters = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetter(c)) continue;
            letters++;
            if (c >= 0x0600 && c <= 0x06FF) arabic++;
            else if (c >= 0x0900 && c <= 0x097F) devanagari++;
            else if (c >= 0x0A00 && c <= 0x0A7F) gurmukhi++;
            else if (c < 0x0250) latin++;
        }
        if (letters == 0) return true;

        switch (lang) {
            case "ur":
            case "ar":
            case "ps":
            case "sd":
                return arabic * 2 >= letters;
            case "hi":
                return devanagari * 2 >= letters;
            case "pa":
                return gurmukhi * 2 >= letters || arabic * 2 >= letters;
            case "en":
                return latin * 2 >= letters;
            default:
                return true;
        }
    }

    private static String detectScriptLang(String text) {
        int arabic = 0;
        int devanagari = 0;
        int gurmukhi = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) arabic++;
            else if (c >= 0x0900 && c <= 0x097F) devanagari++;
            else if (c >= 0x0A00 && c <= 0x0A7F) gurmukhi++;
        }
        if (arabic > devanagari && arabic > gurmukhi) return "ur";
        if (devanagari > gurmukhi) return "hi";
        if (gurmukhi > 0) return "pa";
        return "en";
    }

    // ------------------------------------------------------------------ http

    private static String get(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                return null;
            }
            InputStream in = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) body.append(line).append('\n');
            reader.close();
            return body.toString();
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
