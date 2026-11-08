package com.pulse.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shows the songs inside one playlist. */
public class PlaylistActivity extends Activity implements PlayerEngine.Listener {

    public static final String EXTRA_NAME = "playlist_name";

    private Theme theme;
    private PlayerEngine engine;
    private AlbumArtLoader artLoader;
    private SongAdapter adapter;

    private String name;
    private final List<Song> songs = new ArrayList<>();
    private ListView listView;
    private TextView title;
    private TextView subtitle;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        theme = Theme.current(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);

        name = getIntent().getStringExtra(EXTRA_NAME);
        if (name == null) {
            finish();
            return;
        }

        engine = PlayerEngine.get();
        engine.attach(this);
        artLoader = new AlbumArtLoader(this);

        listView = findViewById(R.id.playlistList);
        title = findViewById(R.id.headerTitle);
        subtitle = findViewById(R.id.headerSubtitle);
        empty = findViewById(R.id.emptyPlaylist);

        paintTheme();
        title.setText(name);

        adapter = new SongAdapter(this, songs, artLoader, theme);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= songs.size()) return;
                engine.setQueue(songs);
                engine.play(songs.get(position));
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                                           final int position, long id) {
                if (position < 0 || position >= songs.size()) return true;
                final Song song = songs.get(position);
                new AlertDialog.Builder(PlaylistActivity.this)
                        .setTitle(song.title)
                        .setItems(new String[]{"Play now", "Remove from playlist"},
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (which == 0) {
                                            engine.setQueue(songs);
                                            engine.play(song);
                                        } else {
                                            Playlists.removeSong(PlaylistActivity.this, name, song.id);
                                            load();
                                        }
                                    }
                                })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            }
        });

        findViewById(R.id.btnClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btnPlayAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (songs.isEmpty()) return;
                engine.setQueue(songs);
                engine.play(songs.get(0));
            }
        });
        findViewById(R.id.btnMenu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });

        load();
    }

    private void paintTheme() {
        findViewById(R.id.root).setBackground(Ui.gradient(theme.bgTop, theme.bgBottom));
        getWindow().setStatusBarColor(theme.bgTop);
        getWindow().setNavigationBarColor(theme.bgTop);

        ((TextView) findViewById(R.id.btnClose)).setTextColor(theme.text);
        title.setTextColor(theme.text);
        subtitle.setTextColor(theme.textDim);
        ((TextView) findViewById(R.id.btnMenu)).setTextColor(theme.text);
        ((TextView) findViewById(R.id.btnPlayAll)).setTextColor(theme.onAccent);
        ((TextView) findViewById(R.id.btnPlayAll)).setBackground(
                Ui.round(theme.accent, 20, theme.accent));
        empty.setTextColor(theme.textDim);

        findViewById(R.id.btnClose).setBackground(
                Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 20, theme.cardStroke));
        findViewById(R.id.btnMenu).setBackground(
                Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 20, theme.cardStroke));
    }

    private void load() {
        songs.clear();
        Playlists.Playlist playlist = Playlists.find(this, name);
        if (playlist == null) {
            finish();
            return;
        }

        Map<Long, Song> byId = new HashMap<>();
        for (Song song : Library.load(this)) byId.put(song.id, song);
        for (Long id : playlist.ids) {
            Song song = byId.get(id);
            if (song != null) songs.add(song);
        }

        adapter.notifyDataSetChanged();
        subtitle.setText(String.format(Locale.US, "%d %s",
                songs.size(), songs.size() == 1 ? "Naat" : "Naats"));
        boolean isEmpty = songs.isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        listView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        findViewById(R.id.btnPlayAll).setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void showMenu() {
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new String[]{"Rename playlist", "Delete playlist"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) showRename();
                                else confirmDelete();
                            }
                        })
                .show();
    }

    private void showRename() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(name);
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Rename playlist")
                .setView(input)
                .setPositiveButton("Rename", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String updated = input.getText().toString().trim();
                        if (updated.isEmpty()) return;
                        if (Playlists.rename(PlaylistActivity.this, name, updated)) {
                            name = updated;
                            title.setText(name);
                        } else {
                            Toast.makeText(PlaylistActivity.this, "Name already used",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete playlist?")
                .setMessage("\"" + name + "\" will be removed. Naats stay on your device.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Playlists.delete(PlaylistActivity.this, name);
                        finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        engine.addListener(this);
        load();
    }

    @Override
    protected void onStop() {
        engine.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        artLoader.shutdown();
        super.onDestroy();
    }

    @Override
    public void onPlayerChanged() {
        Song current = engine.getCurrent();
        adapter.setPlayingId(current == null ? -1 : current.id);
        adapter.notifyDataSetChanged();
    }
}
