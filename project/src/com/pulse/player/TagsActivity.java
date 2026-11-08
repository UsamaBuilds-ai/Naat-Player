package com.pulse.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Tag editor for a naat (title / artist / album / genre) and for every naat
 * in an album (artist / album / genre + cover for all of them).
 *
 * On Android 11+ a batch write request is shown to the user. On older APIs a
 * direct MediaStore update is attempted and any failure is reported plainly.
 */
public class TagsActivity extends Activity {

    public static final String EXTRA_MODE = "extra_mode";   // "song" or "album"
    public static final String EXTRA_ALBUM = "extra_album"; // album name (album mode)
    public static final String EXTRA_SONG_ID = "extra_song_id";

    private static final int REQ_WRITE = 3001;
    private static final int REQ_PICK_IMAGE = 3002;

    private Theme theme;
    private boolean albumMode;
    private Song song;
    private List<Song> albumSongs;

    private EditText fieldTitle;
    private EditText fieldArtist;
    private EditText fieldAlbum;
    private EditText fieldGenre;
    private ImageView artPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        theme = Theme.current(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tags);

        albumMode = "album".equals(getIntent().getStringExtra(EXTRA_MODE));
        String albumName = getIntent().getStringExtra(EXTRA_ALBUM);
        long songId = getIntent().getLongExtra(EXTRA_SONG_ID, -1L);

        fieldTitle = findViewById(R.id.fieldTitle);
        fieldArtist = findViewById(R.id.fieldArtist);
        fieldAlbum = findViewById(R.id.fieldAlbum);
        fieldGenre = findViewById(R.id.fieldGenre);
        artPreview = findViewById(R.id.artPreview);

        TextView header = findViewById(R.id.headerTitle);
        header.setText(albumMode ? "Edit album" : "Edit tags");
        if (albumMode) {
            fieldTitle.setVisibility(View.GONE);
            findViewById(R.id.labelTitle).setVisibility(View.GONE);
        }

        if (albumMode) {
            albumSongs = new ArrayList<>();
            for (Song candidate : Library.load(this)) {
                if (albumName != null && albumName.equals(candidate.album)) {
                    albumSongs.add(candidate);
                }
            }
            if (albumSongs.isEmpty()) {
                Toast.makeText(this, "No naats found in that album", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            Song first = albumSongs.get(0);
            fieldArtist.setText(first.artist);
            fieldAlbum.setText(first.album);
            fieldGenre.setText(first.genre);
        } else {
            for (Song candidate : Library.load(this)) {
                if (candidate.id == songId) {
                    song = candidate;
                    break;
                }
            }
            if (song == null) {
                Toast.makeText(this, "Naat no longer in the library", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            fieldTitle.setText(song.title);
            fieldArtist.setText(song.artist);
            fieldAlbum.setText(song.album);
            fieldGenre.setText(song.genre);
        }

        paintTheme();
        wire();
        refreshPreview();
    }

    private void paintTheme() {
        findViewById(R.id.root).setBackground(Ui.gradient(theme.bgTop, theme.bgBottom));
        getWindow().setStatusBarColor(theme.bgTop);
        getWindow().setNavigationBarColor(theme.bgTop);

        ((TextView) findViewById(R.id.headerTitle)).setTextColor(theme.text);
        findViewById(R.id.btnClose).setBackground(
                Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 20, theme.cardStroke));
        ((TextView) findViewById(R.id.btnClose)).setTextColor(theme.text);
        ((TextView) findViewById(R.id.btnSetArtwork)).setTextColor(theme.accent);
        ((TextView) findViewById(R.id.btnSetArtwork)).setBackground(
                Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 17, theme.cardStroke));
        ((TextView) findViewById(R.id.btnClearArtwork)).setBackground(
                Ui.round((theme.accent & 0x00FFFFFF) | 0x14000000, 17, theme.cardStroke));
        ((TextView) findViewById(R.id.btnSave)).setBackground(Ui.oval(theme.accentSoft, theme.accent));
        ((TextView) findViewById(R.id.btnSave)).setTextColor(theme.onAccent);
        for (int id : new int[]{
                R.id.fieldTitle, R.id.fieldArtist, R.id.fieldAlbum, R.id.fieldGenre}) {
            EditText field = findViewById(id);
            field.setTextColor(theme.text);
            field.setHintTextColor(theme.textDim);
            field.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x14000000, 23, theme.cardStroke));
        }
    }

    private void wire() {
        findViewById(R.id.btnClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        findViewById(R.id.btnSetArtwork).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickArtwork();
            }
        });
        findViewById(R.id.btnClearArtwork).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearArtwork();
            }
        });
    }

    private void refreshPreview() {
        long id = albumMode ? albumSongs.get(0).id : song.id;
        Bitmap bitmap = ArtworkStore.load(this, id, 256);
        if (bitmap == null) {
            bitmap = Artwork.load(this, albumMode ? albumSongs.get(0) : song, 256);
        }
        if (bitmap != null) {
            android.graphics.Bitmap circle = Ui.circle(bitmap);
            artPreview.setImageBitmap(circle != null ? circle : bitmap);
        } else {
            artPreview.setImageResource(R.drawable.widget_disc);
        }
    }

    private void pickArtwork() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQ_PICK_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "No file manager available", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearArtwork() {
        if (albumMode) {
            for (Song candidate : albumSongs) ArtworkStore.clear(this, candidate.id);
        } else {
            ArtworkStore.clear(this, song.id);
        }
        refreshPreview();
        Toast.makeText(this, "Cover removed", Toast.LENGTH_SHORT).show();
    }

    private void save() {
        String artist = fieldArtist.getText().toString().trim();
        String album = fieldAlbum.getText().toString().trim();
        String genre = fieldGenre.getText().toString().trim();
        String title = albumMode ? null : fieldTitle.getText().toString().trim();

        if (!albumMode && (title == null || title.isEmpty())) {
            Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<Uri> uris = new ArrayList<>();
        if (albumMode) {
            for (Song candidate : albumSongs) {
                uris.add(ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, candidate.id));
            }
        } else {
            uris.add(ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id));
        }

        pending = newPending(title, artist, album, genre);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                IntentSender sender = MediaStore.createWriteRequest(getContentResolver(), uris)
                        .getIntentSender();
                startIntentSenderForResult(sender, REQ_WRITE, null, 0, 0, 0);
                return;
            } catch (IllegalArgumentException | SendIntentException e) {
                // Fall through to the direct attempt below.
            }
        }
        applyValues();
    }

    private NewPending pending;

    private NewPending newPending(String title, String artist, String album, String genre) {
        NewPending p = new NewPending();
        p.title = title;
        p.artist = artist;
        p.album = album;
        p.genre = genre;
        return p;
    }

    private static class NewPending {
        String title, artist, album, genre;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_IMAGE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                if (albumMode) {
                    for (Song candidate : albumSongs) ArtworkStore.setOverride(this, candidate.id, uri);
                } else {
                    ArtworkStore.setOverride(this, song.id, uri);
                }
                refreshPreview();
                Toast.makeText(this, "Cover updated", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (requestCode == REQ_WRITE) {
            if (resultCode == RESULT_OK) {
                applyValues();
            } else {
                Toast.makeText(this, "Permission was not granted", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void applyValues() {
        if (pending == null) return;

        ContentValues values = new ContentValues();
        if (pending.title != null) values.put(MediaStore.Audio.Media.TITLE, pending.title);
        values.put(MediaStore.Audio.Media.ARTIST, pending.artist);
        values.put(MediaStore.Audio.Media.ALBUM, pending.album);
        values.put(MediaStore.Audio.Media.GENRE, pending.genre);
        values.put("album_artist", pending.artist);

        List<Song> target = albumMode ? albumSongs : singletonSong();
        PlayerEngine engine = PlayerEngine.get();
        int updated = 0;
        for (Song member : target) {
            Uri uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, member.id);
            try {
                int rows = getContentResolver().update(uri, values, null, null);
                if (rows > 0) {
                    updated++;
                    Song newSong = new Song(member.id, member.albumId,
                            pending.title == null ? member.title : pending.title,
                            pending.artist.isEmpty() ? member.artist : pending.artist,
                            pending.album.isEmpty() ? member.album : pending.album,
                            member.durationMs, member.dateAdded, member.path, pending.genre);
                    engine.updateSong(newSong);
                    if (pending.title != null && !pending.title.equals(member.title)) {
                        Prefs.setRenamedTitle(this, member.id, pending.title);
                    }
                }
            } catch (Exception e) {
                // MediaStore may refuse writes on files the app does not own.
            }
        }

        Prefs.markLibraryDirty(this);
        if (updated == 0) {
            Toast.makeText(this, "Could not update tags. Android may not allow this "
                    + "for files you don't own.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Tags updated (" + updated + ")", Toast.LENGTH_SHORT).show();
        }
        Prefs.markLibraryDirty(this);
        finish();
    }

    private List<Song> singletonSong() {
        List<Song> list = new ArrayList<>();
        list.add(song);
        return list;
    }
}
