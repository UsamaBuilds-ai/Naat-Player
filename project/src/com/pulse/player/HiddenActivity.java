package com.pulse.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Hidden music: hidden songs and hidden folders, with unhide actions. */
public class HiddenActivity extends Activity {

    private static class Row {
        final String title;
        final String subtitle;
        final long songId;
        final String folder;

        Row(String title, String subtitle, long songId, String folder) {
            this.title = title;
            this.subtitle = subtitle;
            this.songId = songId;
            this.folder = folder;
        }
    }

    private Theme theme;
    private ListView list;
    private TextView tabSongs;
    private TextView tabFolders;
    private TextView empty;
    private final List<Row> rows = new ArrayList<>();
    private RowAdapter adapter;
    private boolean foldersTab;
    private List<Song> library = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        theme = Theme.current(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hidden);

        list = findViewById(R.id.hiddenList);
        tabSongs = findViewById(R.id.tabHiddenSongs);
        tabFolders = findViewById(R.id.tabHiddenFolders);
        empty = findViewById(R.id.hiddenEmpty);

        findViewById(R.id.root).setBackground(Ui.gradient(theme.bgTop, theme.bgBottom));
        getWindow().setStatusBarColor(theme.bgTop);
        findViewById(R.id.btnClose).setBackground(
                Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 20, theme.cardStroke));
        ((TextView) findViewById(R.id.btnClose)).setTextColor(theme.text);
        ((TextView) findViewById(R.id.headerTitle)).setTextColor(theme.text);
        empty.setTextColor(theme.textDim);

        findViewById(R.id.btnClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        tabSongs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                foldersTab = false;
                reload();
            }
        });
        tabFolders.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                foldersTab = true;
                reload();
            }
        });

        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                confirmUnhide(rows.get(position));
            }
        });

        reload();
    }

    private void reload() {
        tabSongs.setTextColor(foldersTab ? theme.textDim : theme.accent);
        tabFolders.setTextColor(foldersTab ? theme.accent : theme.textDim);

        rows.clear();
        if (foldersTab) {
            Set<String> hiddenFolders = Prefs.hiddenFolders(this);
            if (library.isEmpty()) library = Library.load(this);
            for (String folder : hiddenFolders) {
                int count = 0;
                for (Song song : library) {
                    if (folder.equals(song.folder())) count++;
                }
                rows.add(new Row(folderName(folder),
                        String.format(Locale.US, "%d naats • %s", count, folder),
                        -1, folder));
            }
        } else {
            Set<String> hiddenSongs = Prefs.hiddenSongs(this);
            if (library.isEmpty()) library = Library.load(this);
            for (Song song : library) {
                if (hiddenSongs.contains(String.valueOf(song.id))) {
                    rows.add(new Row(song.title,
                            (song.artist.isEmpty() ? "Unknown artist" : song.artist)
                                    + " • " + song.folderName(),
                            song.id, null));
                }
            }
        }

        adapter.notifyDataSetChanged();
        boolean isEmpty = rows.isEmpty();
        list.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        empty.setText(foldersTab
                ? "No hidden folders.\nLong-press a Naat in the folder list to hide it."
                : "No hidden naats.\nLong-press a Naat in the library and choose \"Hide Naat\".");
    }

    private String folderName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private void confirmUnhide(final Row row) {
        String name = row.title;
        new AlertDialog.Builder(this)
                .setTitle("Unhide")
                .setMessage("Make \"" + name + "\" visible again?")
                .setPositiveButton("Unhide", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (row.folder != null) {
                            Prefs.setFolderHidden(HiddenActivity.this, row.folder, false);
                        } else {
                            Prefs.setSongHidden(HiddenActivity.this, row.songId, false);
                        }
                        Prefs.markLibraryDirty(HiddenActivity.this);
                        library.clear();
                        reload();
                        Toast.makeText(HiddenActivity.this, "Unhidden", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class RowAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(HiddenActivity.this)
                        .inflate(R.layout.item_group, parent, false);
            }
            Row row = rows.get(position);
            TextView title = view.findViewById(R.id.groupTitle);
            TextView subtitle = view.findViewById(R.id.groupSubtitle);
            TextView badge = view.findViewById(R.id.groupBadge);

            title.setText(row.title);
            title.setTextColor(theme.text);
            subtitle.setText(row.subtitle);
            subtitle.setTextColor(theme.textDim);
            badge.setText("UNHIDE");
            badge.setTextColor(theme.accent);
            badge.setTextSize(9);
            badge.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x22000000, 20, theme.accent));
            return view;
        }
    }
}
