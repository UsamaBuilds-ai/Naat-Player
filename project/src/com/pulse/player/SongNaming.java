package com.pulse.player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Builds a safe file name from the audio tags and its existing library metadata. */
public final class SongNaming {

    private SongNaming() {
    }

    public static String suggest(Song song) {
        String metadata = (song.title + " " + song.artist + " " + song.album + " "
                + song.folderName()).toLowerCase(Locale.US);
        String kind = containsAny(metadata, "naat", "hamd", "nasheed") ? "Naat"
                : containsAny(metadata, "recording", "voice note", "voice", "recorder")
                        ? "Recording" : "Music";
        String title = clean(song.title);
        if (kind.equals("Naat")) {
            title = title.replaceFirst("(?i)^(naat|hamd|nasheed)(\\s*[-_:.]*\\s*)+", "")
                    .trim();
        }
        if (isGeneric(title)) {
            title = new SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US)
                    .format(new Date(song.dateAdded));
        }

        String name = kind.equals("Music") && !song.artist.trim().isEmpty()
                ? clean(song.artist) + " - " + title
                : kind + " - " + title;
        return clean(name);
    }

    private static boolean containsAny(String value, String... words) {
        for (String word : words) {
            if (value.contains(word)) return true;
        }
        return false;
    }

    private static boolean isGeneric(String value) {
        String lower = value.toLowerCase(Locale.US);
        return lower.isEmpty() || containsAny(lower, "recording", "voice note", "voice",
                "recorder", "audio", "track", "unknown", "untitled");
    }

    private static String clean(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", " ")
                .replaceAll("\\s+", " ").trim().replaceAll("[. ]+$", "");
    }
}
