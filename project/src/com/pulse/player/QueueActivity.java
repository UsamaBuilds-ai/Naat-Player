package com.pulse.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

/** Shows the live play queue: tap to jump, long-press to remove. */
public class QueueActivity extends Activity implements PlayerEngine.Listener {

    private Theme theme;
    private PlayerEngine engine;
    private AlbumArtLoader artLoader;
    private SongAdapter adapter;
    private ListView listView;
    private TextView count;
    private TextView empty;
    private TextView btnClear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        theme = Theme.current(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_queue);

        engine = PlayerEngine.get();
        engine.attach(this);
        artLoader = new AlbumArtLoader(this);

        listView = findViewById(R.id.queueList);
        count = findViewById(R.id.queueCount);
        empty = findViewById(R.id.emptyQueue);
        btnClear = findViewById(R.id.btnClear);

        paintTheme();

        adapter = new SongAdapter(this, engine.getQueue(), artLoader, theme);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                engine.playAt(position);
                refresh();
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                                           final int position, long id) {
                if (position < 0 || position >= engine.getQueue().size()) return true;
                final Song song = engine.getQueue().get(position);
                new AlertDialog.Builder(QueueActivity.this)
                        .setTitle(song.title)
                        .setItems(new String[]{"Play now", "Remove from queue"},
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (which == 0) {
                                            engine.playAt(position);
                                        } else {
                                            engine.removeFromQueue(position);
                                        }
                                        refresh();
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
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(QueueActivity.this)
                        .setTitle("Clear queue?")
                        .setMessage("Playback stops and the queue is emptied.")
                        .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                engine.clearQueue();
                                refresh();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        refresh();
    }

    private void paintTheme() {
        findViewById(R.id.root).setBackground(Ui.gradient(theme.bgTop, theme.bgBottom));
        getWindow().setStatusBarColor(theme.bgTop);
        getWindow().setNavigationBarColor(theme.bgTop);

        ((TextView) findViewById(R.id.btnClose)).setTextColor(theme.text);
        ((TextView) findViewById(R.id.headerTitle)).setTextColor(theme.text);
        findViewById(R.id.btnClose).setBackground(
                Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 20, theme.cardStroke));
        btnClear.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 20, theme.cardStroke));
        btnClear.setTextColor(theme.accent);
        count.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 20, theme.accent));
        count.setTextColor(theme.accent);
        empty.setTextColor(theme.textDim);
        ((TextView) findViewById(R.id.queueHint)).setTextColor(theme.textDim);
    }

    private void refresh() {
        Song current = engine.getCurrent();
        adapter.setPlayingId(current == null ? -1 : current.id);
        adapter.notifyDataSetChanged();

        int size = engine.getQueue().size();
        count.setText(String.valueOf(size));
        boolean isEmpty = size == 0;
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        listView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        btnClear.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        engine.addListener(this);
        refresh();
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
        refresh();
    }
}
