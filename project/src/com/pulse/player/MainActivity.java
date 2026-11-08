package com.pulse.player;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.RecoverableSecurityException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity implements PlayerEngine.Listener {

    private static final int REQ_PERM = 1001;
    private static final int REQ_NOTIFICATIONS = 1002;
    private static final int REQ_VIDEO_PERM = 1003;
    private static final int REQ_MEDIA_DELETE = 1004;
    private static final int REQ_MEDIA_WRITE = 1005;
    private static final int REQ_LEGACY_WRITE = 1006;
    private static final int MEDIA_ACTION_DELETE = 1;
    private static final int MEDIA_ACTION_RENAME = 2;
    private static final int TAB_SONGS = 0;
    private static final int TAB_ALBUMS = 1;
    private static final int TAB_ARTISTS = 2;
    private static final int TAB_FOLDERS = 3;
    private static final int TAB_VIDEOS = 4;
    private static final int TAB_GENRES = 5;

    private static final int MODE_ALL = 0;
    private static final int MODE_FAVOURITES = 1;
    private static final int MODE_RECENT = 2;
    private static final int MODE_PLAYLISTS = 3;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Song> allSongs = new ArrayList<>();
    private final List<Song> visibleSongs = new ArrayList<>();
    private final List<VideoItem> videos = new ArrayList<>();
    private final List<GroupAdapter.Group> albums = new ArrayList<>();
    private final List<GroupAdapter.Group> artists = new ArrayList<>();
    private final List<GroupAdapter.Group> folders = new ArrayList<>();
    private final List<GroupAdapter.Group> genres = new ArrayList<>();
    private final List<GroupAdapter.Group> playlistGroups = new ArrayList<>();
    private final Map<Long, Song> songById = new HashMap<>();
    private final Map<Long, Song> pendingRenamedSongs = new HashMap<>();
    private final List<Long> recentIds = new ArrayList<>();
    private final Set<Long> pendingDeletedSongIds = new HashSet<>();

    private Theme theme;
    private PlayerEngine engine;
    private AlbumArtLoader artLoader;
    private SongAdapter songAdapter;
    private VideoAdapter videoAdapter;
    private GroupAdapter groupAdapter;

    private ListView listView;
    private SwipeUpPlayerBar playerBar;
    private LinearLayout emptyState;
    private TextView songCount;
    private TextView npTitle;
    private TextView npArtist;
    private TextView npTime;
    private TextView btnPlay;
    private TextView grantButton;
    private TextView btnTheme;
    private TextView btnEye;
    private TextView btnSettings;
    private TextView emptyGlyph;
    private TextView emptyTitle;
    private TextView emptyHint;
    private TextView tabSongs;
    private TextView tabAlbums;
    private TextView tabArtists;
    private TextView tabFolders;
    private TextView tabVideos;
    private TextView tabGenres;
    private TextView btnFavourite;
    private TextView btnRecent;
    private TextView btnPlaylists;
    private TextView brand;
    private TextView headline;
    private TextView nowArtGlyph;
    private ImageView nowArtImage;
    private View nowArt;
    private SeekBar seekBar;
    private EditText searchInput;
    private View recentSearchScroll;
    private LinearLayout recentSearchRow;

    private int tab = TAB_SONGS;
    private int mode = MODE_ALL;
    private int hiddenCount;
    private String folderFilter;
    private String genreFilter;
    private boolean dragging;
    private boolean askedNotifications;
    private Song pendingMediaSong;
    private String pendingRename;
    private int pendingMediaAction;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            ui.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        theme = Theme.current(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        engine = PlayerEngine.get();
        engine.attach(this);
        artLoader = new AlbumArtLoader(this);

        bindViews();
        paintTheme();

        songAdapter = new SongAdapter(this, visibleSongs, artLoader, theme);
        groupAdapter = new GroupAdapter(this, albums, theme);
        videoAdapter = new VideoAdapter(this, videos, theme);
        listView.setAdapter(songAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mode == MODE_PLAYLISTS) {
                    if (position == 0) {
                        showCreatePlaylistDialog(null);
                        return;
                    }
                    int index = position - 1;
                    if (index < 0 || index >= playlistGroups.size()) return;
                    Intent intent = new Intent(MainActivity.this, PlaylistActivity.class);
                    intent.putExtra(PlaylistActivity.EXTRA_NAME, playlistGroups.get(index).title);
                    startActivity(intent);
                    return;
                }
                if (tab == TAB_VIDEOS) {
                    if (position < 0 || position >= videoAdapter.getCount()) return;
                    VideoItem video = videoAdapter.getItem(position);
                    Intent intent = new Intent(MainActivity.this, VideoPlayerActivity.class);
                    intent.putExtra(VideoPlayerActivity.EXTRA_URI, video.uri.toString());
                    intent.putExtra(VideoPlayerActivity.EXTRA_TITLE, video.title);
                    startActivity(intent);
                    return;
                }
                if (tab == TAB_SONGS) {
                    if (position < 0 || position >= visibleSongs.size()) return;
                    Song song = visibleSongs.get(position);
                    engine.setQueue(visibleSongs);
                    engine.play(song);
                } else if (tab == TAB_FOLDERS) {
                    if (position < 0 || position >= folders.size()) return;
                    GroupAdapter.Group group = folders.get(position);
                    if (group.songs.isEmpty()) return;
                    folderFilter = group.songs.get(0).folder();
                    genreFilter = null;
                    searchInput.setText("");
                    selectTab(TAB_SONGS);
                } else if (tab == TAB_GENRES) {
                    if (position < 0 || position >= genres.size()) return;
                    GroupAdapter.Group group = genres.get(position);
                    if (group.songs.isEmpty()) return;
                    genreFilter = group.songs.get(0).genre;
                    folderFilter = null;
                    searchInput.setText("");
                    selectTab(TAB_SONGS);
                } else {
                    List<GroupAdapter.Group> groups = tab == TAB_ALBUMS ? albums : artists;
                    if (position < 0 || position >= groups.size()) return;
                    searchInput.setText(groups.get(position).title);
                    selectTab(TAB_SONGS);
                }
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (mode == MODE_PLAYLISTS) {
                    if (position > 0 && position - 1 < playlistGroups.size()) {
                        showPlaylistActions(playlistGroups.get(position - 1).title);
                    }
                    return true;
                }
                if (tab == TAB_VIDEOS) return true;
                if (tab == TAB_SONGS) {
                    if (position >= 0 && position < visibleSongs.size()) {
                        showSongActions(visibleSongs.get(position));
                    }
                } else if (tab == TAB_FOLDERS) {
                    if (position >= 0 && position < folders.size()) {
                        showFolderActions(folders.get(position));
                    }
                } else if (tab == TAB_ALBUMS) {
                    if (position >= 0 && position < albums.size()) {
                        showAlbumActions(albums.get(position));
                    }
                }
                return true;
            }
        });

        wireControls();

        paintTransport();
    }

    private void bindViews() {
        listView = findViewById(R.id.songList);
        playerBar = findViewById(R.id.playerBar);
        emptyState = findViewById(R.id.emptyState);
        songCount = findViewById(R.id.songCount);
        npTitle = findViewById(R.id.npTitle);
        npArtist = findViewById(R.id.npArtist);
        npTime = findViewById(R.id.npTime);
        btnPlay = findViewById(R.id.btnPlay);
        grantButton = findViewById(R.id.grantButton);
        btnTheme = findViewById(R.id.btnTheme);
        btnEye = findViewById(R.id.btnEye);
        btnSettings = findViewById(R.id.btnSettings);
        emptyGlyph = findViewById(R.id.emptyGlyph);
        emptyTitle = findViewById(R.id.emptyTitle);
        emptyHint = findViewById(R.id.emptyHint);
        tabSongs = findViewById(R.id.tabSongs);
        tabAlbums = findViewById(R.id.tabAlbums);
        tabArtists = findViewById(R.id.tabArtists);
        tabFolders = findViewById(R.id.tabFolders);
        tabVideos = findViewById(R.id.tabVideos);
        tabGenres = findViewById(R.id.tabGenres);
        btnFavourite = findViewById(R.id.btnFavourite);
        btnRecent = findViewById(R.id.btnRecent);
        btnPlaylists = findViewById(R.id.btnPlaylists);
        brand = findViewById(R.id.brand);
        headline = findViewById(R.id.headline);
        nowArt = findViewById(R.id.nowArt);
        nowArtImage = findViewById(R.id.nowArtImg);
        nowArtGlyph = findViewById(R.id.nowArtGlyph);
        seekBar = findViewById(R.id.seekBar);
        searchInput = findViewById(R.id.searchInput);
        recentSearchScroll = findViewById(R.id.recentSearchScroll);
        recentSearchRow = findViewById(R.id.recentSearchRow);
    }

    private void wireControls() {
        findViewById(R.id.btnPlay).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (engine.getQueue().isEmpty()) engine.setQueue(visibleSongs);
                engine.toggle();
            }
        });
        btnTheme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showThemeChooser();
            }
        });
        btnFavourite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(mode == MODE_FAVOURITES ? MODE_ALL : MODE_FAVOURITES);
            }
        });
        btnRecent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(mode == MODE_RECENT ? MODE_ALL : MODE_RECENT);
            }
        });
        btnPlaylists.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(mode == MODE_PLAYLISTS ? MODE_ALL : MODE_PLAYLISTS);
            }
        });
        btnEye.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean show = !Prefs.showHidden(MainActivity.this);
                Prefs.setShowHidden(MainActivity.this, show);
                loadSongs();
                Toast.makeText(MainActivity.this,
                        show ? "Showing hidden naats and folders"
                                : String.format(Locale.US, "Hidden items hidden (%d)", hiddenCount),
                        Toast.LENGTH_SHORT).show();
            }
        });
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        grantButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tab == TAB_VIDEOS) requestVideoPermission();
                else requestAudioPermission();
            }
        });
        playerBar.setOnSwipeUpListener(new SwipeUpPlayerBar.OnSwipeUpListener() {
            @Override
            public void onSwipeUp() {
                openNowPlaying();
            }
        });
        playerBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNowPlaying();
            }
        });

        View.OnClickListener tabClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                mode = MODE_ALL;
                paintLibraryModes();
                if (id == R.id.tabAlbums) {
                    folderFilter = null;
                    genreFilter = null;
                    selectTab(TAB_ALBUMS);
                } else if (id == R.id.tabArtists) {
                    folderFilter = null;
                    genreFilter = null;
                    selectTab(TAB_ARTISTS);
                } else if (id == R.id.tabFolders) {
                    folderFilter = null;
                    genreFilter = null;
                    selectTab(TAB_FOLDERS);
                } else if (id == R.id.tabGenres) {
                    folderFilter = null;
                    genreFilter = null;
                    selectTab(TAB_GENRES);
                } else {
                    folderFilter = null;
                    genreFilter = null;
                    selectTab(TAB_SONGS);
                }
            }
        };
        tabSongs.setOnClickListener(tabClick);
        tabAlbums.setOnClickListener(tabClick);
        tabArtists.setOnClickListener(tabClick);
        tabFolders.setOnClickListener(tabClick);
        tabGenres.setOnClickListener(tabClick);
        tabVideos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mode = MODE_ALL;
                folderFilter = null;
                selectTab(TAB_VIDEOS);
                if (hasVideoPermission()) loadVideos();
                else showVideoPermissionState();
            }
        });

        searchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    String query = searchInput.getText().toString().trim();
                    Prefs.addRecentSearch(MainActivity.this, query);
                    applyFilter(query);
                    return true;
                }
                return false;
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    int duration = engine.duration();
                    if (duration > 0) engine.seekTo((int) (duration * (progress / 1000f)));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
            }
        });
    }

    // ------------------------------------------------------------------ theme

    private void paintTheme() {
        findViewById(R.id.root).setBackground(Ui.gradient(theme.bgTop, theme.bgBottom));
        getWindow().setStatusBarColor(theme.bgTop);
        getWindow().setNavigationBarColor(theme.bgTop);

        playerBar.setBackground(Ui.card(theme.cardTop, theme.cardBottom, theme.cardStroke));
        songCount.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 20, theme.accent));
        songCount.setTextColor(theme.accent);
        searchInput.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x14000000, 21, theme.cardStroke));
        searchInput.setTextColor(theme.text);
        searchInput.setHintTextColor(theme.textDim);

        btnTheme.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 19, theme.cardStroke));
        btnTheme.setTextColor(theme.text);
        btnSettings.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 19, theme.cardStroke));
        btnSettings.setTextColor(theme.text);

        btnPlay.setBackground(Ui.oval(theme.accentSoft, theme.accent));
        btnPlay.setTextColor(theme.onAccent);

        nowArt.setBackground(Ui.art(theme.cardTop, theme.accent, 12));
        nowArtGlyph.setTextColor(theme.onAccent);
        emptyGlyph.setBackground(Ui.art(theme.cardTop, theme.accent, 16));
        emptyGlyph.setTextColor(theme.onAccent);

        grantButton.setBackground(Ui.oval(theme.accentSoft, theme.accent));
        grantButton.setTextColor(theme.onAccent);

        brand.setTextColor(theme.accent);
        headline.setTextColor(theme.text);
        npTitle.setTextColor(theme.text);
        npArtist.setTextColor(theme.textDim);
        npTime.setTextColor(theme.accentSoft);
        emptyTitle.setTextColor(theme.text);
        emptyHint.setTextColor(theme.textDim);

        Ui.tintSeekBar(seekBar, theme);
        paintTabs();
        paintEye();
        paintLibraryModes();
    }

    private void paintLibraryModes() {
        styleModeButton(btnFavourite, mode == MODE_FAVOURITES);
        styleModeButton(btnRecent, mode == MODE_RECENT);
        styleModeButton(btnPlaylists, mode == MODE_PLAYLISTS);
        boolean hasFavourites = !Prefs.favoriteIds(this).isEmpty();
        btnFavourite.setText(hasFavourites ? "♥ My Favourite" : "♡ My Favourite");
    }

    private void styleModeButton(TextView button, boolean active) {
        button.setTextColor(active ? theme.onAccent : theme.textDim);
        button.setBackground(Ui.round(active ? theme.accent
                        : ((theme.accent & 0x00FFFFFF) | 0x14000000),
                16, active ? theme.accent : theme.cardStroke));
    }

    private void paintTabs() {
        boolean all = mode == MODE_ALL;
        int active = theme.accent;
        int idle = theme.textDim;
        tabSongs.setTextColor(all && tab == TAB_SONGS ? active : idle);
        tabAlbums.setTextColor(all && tab == TAB_ALBUMS ? active : idle);
        tabArtists.setTextColor(all && tab == TAB_ARTISTS ? active : idle);
        tabFolders.setTextColor(all && tab == TAB_FOLDERS ? active : idle);
        tabGenres.setTextColor(all && tab == TAB_GENRES ? active : idle);
        tabVideos.setTextColor(all && tab == TAB_VIDEOS ? active : idle);
    }

    private void paintEye() {
        boolean show = Prefs.showHidden(this);
        btnEye.setText(show ? "◉" : "◎");
        btnEye.setTextColor(show ? theme.accent : theme.textDim);
        btnEye.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | (show ? 0x1F000000 : 0x0D000000),
                19, show ? theme.accent : theme.cardStroke));
    }

    private void showThemeChooser() {
        final AlertDialog[] holder = new AlertDialog[1];
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Theme")
                .setView(Ui.themeCards(this, theme, new Ui.ThemePick() {
                    @Override
                    public void onPick(Theme picked) {
                        Theme.select(MainActivity.this, picked);
                        if (holder[0] != null) holder[0].dismiss();
                        recreate();
                    }
                }))
                .create();
        holder[0] = dialog;
        dialog.show();
    }

    private void paintRecentSearches(String query) {
        boolean show = query == null || query.trim().isEmpty();
        if (!show) {
            recentSearchScroll.setVisibility(View.GONE);
            return;
        }
        java.util.List<String> searches = Prefs.recentSearches(this);
        recentSearchRow.removeAllViews();
        if (searches.isEmpty()) {
            recentSearchScroll.setVisibility(View.GONE);
            return;
        }
        for (final String search : searches) {
            TextView chip = new TextView(this);
            chip.setText("◷  " + search);
            chip.setTextColor(theme.textDim);
            chip.setTextSize(11);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            chip.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x14000000, 15, theme.cardStroke));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.rightMargin = dp(6);
            chip.setLayoutParams(params);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchInput.setText(search);
                    searchInput.setSelection(search.length());
                }
            });
            recentSearchRow.addView(chip);
        }
        TextView clear = new TextView(this);
        clear.setText("✕");
        clear.setTextColor(theme.textDim);
        clear.setTextSize(12);
        clear.setPadding(dp(12), dp(6), dp(12), dp(6));
        clear.setBackground(Ui.round(0x14000000, 15, 0));
        clear.setContentDescription("Clear recent searches");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.clearRecentSearches(MainActivity.this);
                paintRecentSearches(searchInput.getText().toString());
            }
        });
        recentSearchRow.addView(clear);
        recentSearchScroll.setVisibility(View.VISIBLE);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ tabs

    private void selectTab(int which) {
        tab = which;
        if (tab == TAB_SONGS) {
            listView.setAdapter(songAdapter);
        } else if (tab == TAB_VIDEOS) {
            listView.setAdapter(videoAdapter);
        } else {
            List<GroupAdapter.Group> groups = tab == TAB_ALBUMS ? albums
                    : tab == TAB_ARTISTS ? artists
                    : tab == TAB_GENRES ? genres : folders;
            groupAdapter = new GroupAdapter(this, groups, theme);
            listView.setAdapter(groupAdapter);
        }
        paintTabs();
        applyFilter(searchInput.getText().toString());
    }

    private void reconcilePendingMediaChanges(List<Song> loaded) {
        Set<Long> loadedIds = new HashSet<>();
        for (Song song : loaded) loadedIds.add(song.id);
        pendingDeletedSongIds.retainAll(loadedIds);
        for (int i = loaded.size() - 1; i >= 0; i--) {
            if (pendingDeletedSongIds.contains(loaded.get(i).id)) loaded.remove(i);
        }

        for (Song pending : new ArrayList<>(pendingRenamedSongs.values())) {
            int index = -1;
            for (int i = 0; i < loaded.size(); i++) {
                if (loaded.get(i).id == pending.id) {
                    index = i;
                    break;
                }
            }
            if (index >= 0 && loaded.get(index).title.equals(pending.title)
                    && loaded.get(index).path.equals(pending.path)) {
                pendingRenamedSongs.remove(pending.id);
            } else if (index >= 0) {
                loaded.set(index, pending);
            } else {
                loaded.add(pending);
            }
        }
    }

    /** Switches between the plain library and the quick filters above the list. */
    private void setMode(int newMode) {
        mode = newMode;
        if (mode == MODE_FAVOURITES || mode == MODE_RECENT) {
            folderFilter = null;
            genreFilter = null;
            tab = TAB_SONGS;
        }

        if (mode == MODE_PLAYLISTS) {
            buildPlaylistGroups();
            groupAdapter = new GroupAdapter(this, playlistGroups, theme);
            listView.setAdapter(groupAdapter);
        } else if (tab == TAB_SONGS) {
            listView.setAdapter(songAdapter);
        } else if (tab == TAB_VIDEOS) {
            listView.setAdapter(videoAdapter);
        } else {
            List<GroupAdapter.Group> groups = tab == TAB_ALBUMS ? albums
                    : tab == TAB_ARTISTS ? artists
                    : tab == TAB_GENRES ? genres : folders;
            groupAdapter = new GroupAdapter(this, groups, theme);
            listView.setAdapter(groupAdapter);
        }

        paintTabs();
        paintLibraryModes();
        applyFilter(searchInput.getText().toString());
    }

    // ------------------------------------------------------------------ library

    @Override
    protected void onStart() {
        super.onStart();

        if (Theme.current(this) != theme) {
            recreate();
            return;
        }

        if (Prefs.keepScreenOn(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        engine.addListener(this);
        ui.post(ticker);
        ensureNotificationPermission();

        if (hasAudioPermission()) {
            recentIds.clear();
            recentIds.addAll(Usage.recent(this));
            songAdapter.notifyDataSetChanged();
            paintLibraryModes();
            if (mode == MODE_PLAYLISTS) {
                buildPlaylistGroups();
                if (groupAdapter != null) groupAdapter.notifyDataSetChanged();
            }
            if (!allSongs.isEmpty()) applyFilter(searchInput.getText().toString());
        }

        if (tab == TAB_VIDEOS) {
            if (hasVideoPermission()) loadVideos();
            else showVideoPermissionState();
        } else if (!hasAudioPermission()) {
            showPermissionState();
        } else if (Prefs.consumeLibraryDirty(this) || allSongs.isEmpty()) {
            loadSongs();
        } else {
            paintTransport();
        }
    }

    @Override
    protected void onStop() {
        ui.removeCallbacks(ticker);
        engine.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        artLoader.shutdown();
        super.onDestroy();
    }

    private boolean hasAudioPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        String permission = Build.VERSION.SDK_INT >= 33
                ? "android.permission.READ_MEDIA_AUDIO"
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** Android 13+ needs an explicit permission before the media notification is visible. */
    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || askedNotifications) return;
        if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                == PackageManager.PERMISSION_GRANTED) return;
        askedNotifications = true;
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},
                REQ_NOTIFICATIONS);
    }

    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            loadSongs();
            return;
        }
        String permission = Build.VERSION.SDK_INT >= 33
                ? "android.permission.READ_MEDIA_AUDIO"
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        requestPermissions(new String[]{permission}, REQ_PERM);
    }

    private boolean hasVideoPermission() {
        if (Build.VERSION.SDK_INT < 33) return hasAudioPermission();
        if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)
                == PackageManager.PERMISSION_GRANTED) return true;
        return Build.VERSION.SDK_INT >= 34
                && checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestVideoPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            if (hasAudioPermission()) {
                loadVideos();
            } else {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQ_VIDEO_PERM);
            }
            return;
        }
        String[] permissions = Build.VERSION.SDK_INT >= 34
                ? new String[]{Manifest.permission.READ_MEDIA_VIDEO,
                        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"}
                : new String[]{Manifest.permission.READ_MEDIA_VIDEO};
        requestPermissions(permissions, REQ_VIDEO_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_PERM) {
            boolean granted = results.length > 0
                    && results[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) loadSongs();
            else {
                showPermissionState();
                Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_VIDEO_PERM) {
            if (hasVideoPermission()) {
                loadVideos();
            } else {
                showVideoPermissionState();
                Toast.makeText(this, R.string.video_permission_needed, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_LEGACY_WRITE) {
            Song song = pendingMediaSong;
            String rename = pendingRename;
            int action = pendingMediaAction;
            pendingMediaSong = null;
            pendingRename = null;
            pendingMediaAction = 0;
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED
                    && song != null) {
                if (action == MEDIA_ACTION_DELETE) deleteSongNow(song, true);
                else if (action == MEDIA_ACTION_RENAME) renameSongNow(song, rename, true);
            } else {
                Toast.makeText(this, "Storage permission is required for this change",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_MEDIA_DELETE && requestCode != REQ_MEDIA_WRITE) return;
        Song song = pendingMediaSong;
        String rename = pendingRename;
        int action = pendingMediaAction;
        pendingMediaSong = null;
        pendingRename = null;
        pendingMediaAction = 0;
        if (resultCode != RESULT_OK || song == null) {
            Toast.makeText(this, "Media change was not approved", Toast.LENGTH_SHORT).show();
            return;
        }
        if (action == MEDIA_ACTION_DELETE) deleteSongNow(song, true);
        else if (action == MEDIA_ACTION_RENAME) renameSongNow(song, rename, true);
    }

    private void loadVideos() {
        try {
            videos.clear();
            videos.addAll(VideoLibrary.load(this));
            applyFilter(searchInput.getText().toString());
        } catch (SecurityException e) {
            showVideoPermissionState();
            Toast.makeText(this, R.string.video_permission_needed, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Loads the library and drops hidden songs / hidden folders unless the user asked to see them.
     * The MediaStore scan runs in the background; a dialog appears only for slow scans.
     */
    private void loadSongs() {
        final android.app.AlertDialog scanning = new android.app.AlertDialog.Builder(this)
                .setTitle("Scanning library…")
                .setMessage("Reading your naats from storage")
                .setCancelable(false)
                .create();
        final boolean[] finished = {false};
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!finished[0] && !scanning.isShowing()) scanning.show();
            }
        }, 400);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Song> loaded = Library.load(MainActivity.this);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        finished[0] = true;
                        applyLoadedSongs(loaded);
                        if (scanning.isShowing()) scanning.dismiss();
                    }
                });
            }
        }, "library-scan").start();
    }

    private void applyLoadedSongs(List<Song> loaded) {
        reconcilePendingMediaChanges(loaded);
        applyRenamedTitles(loaded);
        allSongs.clear();
        engine.restorePlayback(loaded, true);

        Set<String> hiddenSongs = Prefs.hiddenSongs(this);
        Set<String> hiddenFolders = Prefs.hiddenFolders(this);
        boolean showHidden = Prefs.showHidden(this);
        boolean showDotFolders = Prefs.showHiddenFiles(this);

        hiddenCount = 0;
        for (Song song : loaded) {
            boolean hidden = hiddenSongs.contains(String.valueOf(song.id))
                    || hiddenFolders.contains(song.folder())
                    || (!showDotFolders && hasDotSegment(song.path));
            if (hidden) {
                hiddenCount++;
                if (!showHidden) continue;
            }
            allSongs.add(song);
        }

        if (folderFilter != null) {
            boolean stillThere = false;
            for (Song song : allSongs) {
                if (folderFilter.equals(song.folder())) {
                    stillThere = true;
                    break;
                }
            }
            if (!stillThere) folderFilter = null;
        }

        if (genreFilter != null) {
            String want = genreFilter;
            boolean stillThere = false;
            for (Song song : allSongs) {
                if (equalsIgnoreCase(song.genre, want)) {
                    stillThere = true;
                    break;
                }
            }
            if (!stillThere) genreFilter = null;
        }

        songById.clear();
        for (Song song : allSongs) songById.put(song.id, song);
        recentIds.clear();
        recentIds.addAll(Usage.recent(this));

        buildGroups();
        paintEye();
        paintLibraryModes();
        applyFilter(searchInput.getText().toString());
    }

    /** Re-applies local nicknames over whatever MediaStore currently reports. */
    private void applyRenamedTitles(List<Song> loaded) {
        for (int i = 0; i < loaded.size(); i++) {
            Song song = loaded.get(i);
            String nick = Prefs.renamedTitle(this, song.id);
            if (nick != null && !nick.equals(song.title)) {
                loaded.set(i, new Song(song.id, song.albumId, nick, song.artist,
                        song.album, song.durationMs, song.dateAdded, song.path, song.genre));
            }
        }
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private static boolean hasDotSegment(String path) {
        if (path == null) return false;
        for (String part : path.split("/")) {
            if (part.length() > 1 && part.charAt(0) == '.') return true;
        }
        return false;
    }

    private void buildGroups() {
        Map<String, GroupAdapter.Group> albumMap = new LinkedHashMap<>();
        Map<String, GroupAdapter.Group> artistMap = new LinkedHashMap<>();
        Map<String, GroupAdapter.Group> folderMap = new LinkedHashMap<>();
        Map<String, GroupAdapter.Group> genreMap = new LinkedHashMap<>();

        for (Song song : allSongs) {
            String albumKey = song.album.isEmpty() ? "Unknown album" : song.album;
            GroupAdapter.Group album = albumMap.get(albumKey);
            if (album == null) {
                album = new GroupAdapter.Group(albumKey,
                        song.artist.isEmpty() ? "Various artists" : song.artist);
                albumMap.put(albumKey, album);
            }
            album.songs.add(song);

            String artistKey = song.artist.isEmpty() ? "Unknown artist" : song.artist;
            GroupAdapter.Group artist = artistMap.get(artistKey);
            if (artist == null) {
                artist = new GroupAdapter.Group(artistKey, "Artist");
                artistMap.put(artistKey, artist);
            }
            artist.songs.add(song);

            String folderPath = song.folder();
            GroupAdapter.Group folder = folderMap.get(folderPath);
            if (folder == null) {
                folder = new GroupAdapter.Group(song.folderName(), "Folder");
                folderMap.put(folderPath, folder);
            }
            folder.songs.add(song);

            String genreKey = song.genre == null || song.genre.trim().isEmpty()
                    ? "Unknown genre" : song.genre.trim();
            GroupAdapter.Group genre = genreMap.get(genreKey);
            if (genre == null) {
                genre = new GroupAdapter.Group(genreKey, "Genre");
                genreMap.put(genreKey, genre);
            }
            genre.songs.add(song);
        }

        albums.clear();
        albums.addAll(albumMap.values());

        artists.clear();
        artists.addAll(artistMap.values());

        folders.clear();
        folders.addAll(folderMap.values());

        genres.clear();
        genres.addAll(genreMap.values());

        List<GroupAdapter.Group> orderedGenres = new ArrayList<>(genres);
        Collections.sort(orderedGenres, new Comparator<GroupAdapter.Group>() {
            @Override
            public int compare(GroupAdapter.Group a, GroupAdapter.Group b) {
                if ("Unknown genre".equalsIgnoreCase(a.title)) return 1;
                if ("Unknown genre".equalsIgnoreCase(b.title)) return -1;
                return a.title.compareToIgnoreCase(b.title);
            }
        });
        genres.clear();
        genres.addAll(orderedGenres);

        // Folder rows are ordered by name like the rest of the library.
        List<GroupAdapter.Group> ordered = new ArrayList<>(folders);
        Collections.sort(ordered, new Comparator<GroupAdapter.Group>() {
            @Override
            public int compare(GroupAdapter.Group a, GroupAdapter.Group b) {
                return a.title.compareToIgnoreCase(b.title);
            }
        });
        folders.clear();
        folders.addAll(ordered);
    }

    private void applyFilter(String query) {
        paintRecentSearches(query);
        if (tab == TAB_VIDEOS && mode == MODE_ALL) {
            if (!hasVideoPermission()) {
                showVideoPermissionState();
                return;
            }
            videoAdapter.filter(query);
            songCount.setText(String.format(Locale.US, "%d videos", videoAdapter.getCount()));
            emptyTitle.setText(R.string.video_empty);
            emptyHint.setText(R.string.video_empty_hint);
            boolean empty = videoAdapter.getCount() == 0;
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            listView.setVisibility(empty ? View.GONE : View.VISIBLE);
            grantButton.setVisibility(hasVideoPermission() ? View.GONE : View.VISIBLE);
            return;
        }
        visibleSongs.clear();

        if (mode == MODE_RECENT) {
            for (Long id : recentIds) {
                Song song = songById.get(id);
                if (song != null && song.matches(query)) visibleSongs.add(song);
            }
        } else {
            for (Song song : allSongs) {
                if (mode == MODE_FAVOURITES && !Prefs.isFavorite(this, song.id)) continue;
                if (folderFilter != null && !folderFilter.equals(song.folder())) continue;
                if (genreFilter != null && !equalsIgnoreCase(song.genre, genreFilter)) continue;
                if (song.matches(query)) visibleSongs.add(song);
            }
        }

        songAdapter.notifyDataSetChanged();
        if (groupAdapter != null) groupAdapter.notifyDataSetChanged();

        if (mode == MODE_PLAYLISTS) {
            int count = Math.max(0, playlistGroups.size() - 1);
            songCount.setText(String.format(Locale.US, "%d %s", count,
                    count == 1 ? "playlist" : "playlists"));
            emptyState.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            grantButton.setVisibility(View.GONE);
            return;
        }

        if (mode == MODE_FAVOURITES) {
            emptyTitle.setText("No favourites yet");
            emptyHint.setText("Long-press a Naat, then tap Add to Favourites");
        } else if (mode == MODE_RECENT) {
            emptyTitle.setText("Nothing played yet");
            emptyHint.setText("Naats you play will show up here");
        } else {
            emptyTitle.setText(R.string.empty_library);
            emptyHint.setText(R.string.empty_hint);
        }

        boolean filtered = query != null && query.trim().length() > 0;
        if (mode == MODE_FAVOURITES) {
            songCount.setText(String.format(Locale.US, "%d %s", visibleSongs.size(),
                    filtered ? "found" : "favourites"));
        } else if (mode == MODE_RECENT) {
            songCount.setText(String.format(Locale.US, "%d recent", visibleSongs.size()));
        } else if (tab == TAB_SONGS) {
            if (folderFilter != null) {
                String name = folderFilter;
                int slash = name.lastIndexOf('/');
                if (slash >= 0) name = name.substring(slash + 1);
                songCount.setText(String.format(Locale.US, "%d in %s", visibleSongs.size(), name));
            } else if (genreFilter != null) {
                songCount.setText(String.format(Locale.US, "%d in %s", visibleSongs.size(),
                        genreFilter.isEmpty() ? "Unknown genre" : genreFilter));
            } else {
                songCount.setText(String.format(Locale.US, "%d%s", visibleSongs.size(),
                        filtered ? " found" : " naats"));
            }
        } else {
            songCount.setText(String.format(Locale.US, "%d %s",
                    tab == TAB_ALBUMS ? albums.size()
                            : tab == TAB_ARTISTS ? artists.size()
                            : tab == TAB_GENRES ? genres.size() : folders.size(),
                    tab == TAB_ALBUMS ? "albums"
                            : tab == TAB_ARTISTS ? "artists"
                            : tab == TAB_GENRES ? "genres" : "folders"));
        }

        boolean empty = visibleSongs.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
        grantButton.setVisibility(hasAudioPermission() ? View.GONE : View.VISIBLE);
    }

    private void showPermissionState() {
        visibleSongs.clear();
        songAdapter.notifyDataSetChanged();
        emptyTitle.setText(R.string.permission_needed);
        emptyHint.setText(R.string.permission_hint);
        songCount.setText("");
        emptyState.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        grantButton.setVisibility(View.VISIBLE);
    }

    private void showVideoPermissionState() {
        emptyTitle.setText(R.string.video_permission_needed);
        emptyHint.setText(R.string.video_permission_hint);
        songCount.setText("");
        emptyState.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        grantButton.setVisibility(View.VISIBLE);
    }

    // --------------------------------------------------------------- hide/unhide

    private void showSongActions(final Song song) {
        final boolean songHidden = Prefs.isSongHidden(this, song.id);
        final boolean folderHidden = Prefs.isFolderHidden(this, song.folder());
        final boolean favourite = Prefs.isFavorite(this, song.id);
        final String folderName = song.folderName();

        List<String> options = new ArrayList<>();
        options.add(favourite ? "Remove from Favourites" : "Add to Favourites");
        options.add("Edit tags…");
        options.add("Add to playlist…");
        options.add("Play next");
        options.add("Add to queue");
        options.add("Rename Naat");
        options.add("Auto name");
        options.add("Delete Naat");
        if (songHidden) options.add("Unhide Naat");
        if (folderHidden) options.add("Unhide folder \"" + folderName + "\"");
        if (!songHidden) options.add("Hide Naat");
        if (!folderHidden) options.add("Hide folder \"" + folderName + "\"");

        new AlertDialog.Builder(this)
                .setTitle(song.title)
                .setItems(options.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String choice = options.get(which);
                        if (choice.startsWith("Add to Favourites")
                                || choice.startsWith("Remove from Favourites")) {
                            boolean added = Prefs.toggleFavorite(MainActivity.this, song.id);
                            songAdapter.notifyDataSetChanged();
                            paintLibraryModes();
                            Toast.makeText(MainActivity.this,
                                    added ? "Added to Favourites" : "Removed from Favourites",
                                    Toast.LENGTH_SHORT).show();
                            if (mode == MODE_FAVOURITES) applyFilter(searchInput.getText().toString());
                        } else if (choice.startsWith("Edit tags")) {
                            Intent intent = new Intent(MainActivity.this, TagsActivity.class);
                            intent.putExtra(TagsActivity.EXTRA_MODE, "song");
                            intent.putExtra(TagsActivity.EXTRA_SONG_ID, song.id);
                            startActivity(intent);
                        } else if (choice.startsWith("Add to playlist")) {
                            showAddToPlaylist(song);
                        } else if (choice.startsWith("Play next")) {
                            engine.playNext(song);
                            Toast.makeText(MainActivity.this, "Playing next",
                                    Toast.LENGTH_SHORT).show();
                        } else if (choice.startsWith("Add to queue")) {
                            engine.addToQueue(song);
                            Toast.makeText(MainActivity.this, "Added to queue",
                                    Toast.LENGTH_SHORT).show();
                        } else if (choice.startsWith("Rename Naat")) {
                            showRenameDialog(song);
                        } else if (choice.startsWith("Auto name")) {
                            String name = SongNaming.suggest(song);
                            if (withExtension(new File(song.path).getName(), name)
                                    .equals(new File(song.path).getName())) {
                                Toast.makeText(MainActivity.this, "This Naat is already named",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                renameSongNow(song, name, false);
                            }
                        } else if (choice.startsWith("Delete Naat")) {
                            confirmDeleteSong(song);
                        } else if (choice.startsWith("Unhide Naat")) {
                            Prefs.setSongHidden(MainActivity.this, song.id, false);
                            toastAndReload("Naat unhidden");
                        } else if (choice.startsWith("Unhide folder")) {
                            Prefs.setFolderHidden(MainActivity.this, song.folder(), false);
                            toastAndReload("Folder unhidden");
                        } else if (choice.startsWith("Hide Naat")) {
                            Prefs.setSongHidden(MainActivity.this, song.id, true);
                            toastAndReload("Naat hidden");
                        } else if (choice.startsWith("Hide folder")) {
                            Prefs.setFolderHidden(MainActivity.this, song.folder(), true);
                            toastAndReload("Folder hidden");
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRenameDialog(final Song song) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(song.title);
        input.setSelection(input.length());
        new AlertDialog.Builder(this)
                .setTitle("Rename Naat")
                .setView(input)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Name cannot be empty",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        renameSongNow(song, name, false);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteSong(final Song song) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Naat?")
                .setMessage("This will delete \"" + song.title + "\" from your device.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteSongNow(song, false);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Uri songUri(Song song) {
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id);
    }

    private void requestMediaGrant(Song song, String rename, int action, int requestCode,
                                   IntentSender sender) {
        pendingMediaSong = song;
        pendingRename = rename;
        pendingMediaAction = action;
        try {
            startIntentSenderForResult(sender, requestCode, null, 0, 0, 0);
        } catch (Exception e) {
            pendingMediaSong = null;
            pendingRename = null;
            pendingMediaAction = 0;
            Toast.makeText(this, "Could not request media permission",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void deleteSongNow(Song song, boolean permissionGranted) {
        Uri uri = songUri(song);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !permissionGranted
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestLegacyWritePermission(song, null, MEDIA_ACTION_DELETE);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !permissionGranted) {
            try {
                IntentSender sender = MediaStore.createDeleteRequest(getContentResolver(),
                        Collections.singletonList(uri)).getIntentSender();
                requestMediaGrant(song, null, MEDIA_ACTION_DELETE, REQ_MEDIA_DELETE, sender);
            } catch (Exception e) {
                Toast.makeText(this, "Could not request delete permission",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        try {
            int deleted = getContentResolver().delete(uri, null, null);
            if (deleted == 0) {
                Toast.makeText(this, "Naat could not be deleted", Toast.LENGTH_LONG).show();
                return;
            }
            pendingDeletedSongIds.add(song.id);
            pendingRenamedSongs.remove(song.id);
            Prefs.clearRenamedTitle(this, song.id);
            engine.removeSong(song.id);
            allSongs.remove(song);
            songById.remove(song.id);
            recentIds.remove(song.id);
            buildGroups();
            applyFilter(searchInput.getText().toString());
            Prefs.markLibraryDirty(this);
            Toast.makeText(this, "Naat deleted", Toast.LENGTH_SHORT).show();
        } catch (RecoverableSecurityException e) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && !permissionGranted) {
                requestMediaGrant(song, null, MEDIA_ACTION_DELETE, REQ_MEDIA_DELETE,
                        e.getUserAction().getActionIntent().getIntentSender());
            } else {
                Toast.makeText(this, "Delete permission was not granted",
                        Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "Delete permission was not granted",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not delete Naat", Toast.LENGTH_LONG).show();
        }
    }

    private void renameSongNow(Song song, String name, boolean permissionGranted) {
        Uri uri = songUri(song);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !permissionGranted
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestLegacyWritePermission(song, name, MEDIA_ACTION_RENAME);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !permissionGranted) {
            try {
                IntentSender sender = MediaStore.createWriteRequest(getContentResolver(),
                        Collections.singletonList(uri)).getIntentSender();
                requestMediaGrant(song, name, MEDIA_ACTION_RENAME, REQ_MEDIA_WRITE, sender);
            } catch (Exception e) {
                Toast.makeText(this, "Could not request rename permission",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        File legacyTarget = null;
        boolean legacyFileRenamed = false;
        File oldFile = new File(song.path);
        String fileName = withExtension(oldFile.getName(), name);
        String title = fileName;
        int extension = title.lastIndexOf('.');
        if (extension > 0) title = title.substring(0, extension);

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.TITLE, title);
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            File newFile = new File(oldFile.getParentFile(), fileName);
            if (!oldFile.equals(newFile) && newFile.exists()) {
                Toast.makeText(this, "A file with that name already exists",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (!oldFile.equals(newFile) && !oldFile.renameTo(newFile)) {
                Toast.makeText(this, "Naat could not be renamed", Toast.LENGTH_LONG).show();
                return;
            }
            legacyTarget = newFile;
            legacyFileRenamed = !oldFile.equals(newFile);
            values.put(MediaStore.MediaColumns.DATA, newFile.getAbsolutePath());
        }

        try {
            int updated = getContentResolver().update(uri, values, null, null);
            if (updated == 0) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    File newFile = new File(oldFile.getParentFile(), fileName);
                    if (!oldFile.equals(newFile)) newFile.renameTo(oldFile);
                }
                Toast.makeText(this, "Naat could not be renamed", Toast.LENGTH_LONG).show();
                return;
            }
            String updatedTitle = title;
            File updatedFile = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    ? legacyTarget : new File(oldFile.getParentFile(), fileName);
            Song updatedSong = new Song(song.id, song.albumId, updatedTitle, song.artist,
                    song.album, song.durationMs, song.dateAdded, updatedFile.getAbsolutePath());
            pendingRenamedSongs.put(song.id, updatedSong);
            Prefs.setRenamedTitle(this, song.id, updatedSong.title);
            for (int i = 0; i < allSongs.size(); i++) {
                if (allSongs.get(i).id == song.id) allSongs.set(i, updatedSong);
            }
            songById.put(song.id, updatedSong);
            engine.updateSong(updatedSong);
            buildGroups();
            applyFilter(searchInput.getText().toString());
            Prefs.markLibraryDirty(this);
            Toast.makeText(this, "Naat renamed", Toast.LENGTH_SHORT).show();
        } catch (RecoverableSecurityException e) {
            if (legacyFileRenamed) legacyTarget.renameTo(oldFile);
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && !permissionGranted) {
                requestMediaGrant(song, name, MEDIA_ACTION_RENAME, REQ_MEDIA_WRITE,
                        e.getUserAction().getActionIntent().getIntentSender());
            } else {
                Toast.makeText(this, "Rename permission was not granted",
                        Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException e) {
            if (legacyFileRenamed) legacyTarget.renameTo(oldFile);
            Toast.makeText(this, "Rename permission was not granted",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            if (legacyFileRenamed) legacyTarget.renameTo(oldFile);
            Toast.makeText(this, "Could not rename Naat", Toast.LENGTH_LONG).show();
        }
    }

    private void requestLegacyWritePermission(Song song, String rename, int action) {
        pendingMediaSong = song;
        pendingRename = rename;
        pendingMediaAction = action;
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQ_LEGACY_WRITE);
    }

    private String withExtension(String oldName, String name) {
        int dot = oldName.lastIndexOf('.');
        String extension = dot > 0 ? oldName.substring(dot) : "";
        return extension.isEmpty() || name.toLowerCase(Locale.US)
                .endsWith(extension.toLowerCase(Locale.US)) ? name : name + extension;
    }

    // ------------------------------------------------------------------ playlists

    private void buildPlaylistGroups() {
        playlistGroups.clear();
        GroupAdapter.Group create = new GroupAdapter.Group("＋  New playlist", "Create a playlist");
        create.count = 0;
        playlistGroups.add(create);

        for (Playlists.Playlist playlist : Playlists.all(this)) {
            GroupAdapter.Group group = new GroupAdapter.Group(playlist.name,
                    playlist.ids.isEmpty() ? "Empty playlist"
                            : playlist.ids.size() + (playlist.ids.size() == 1 ? " Naat" : " Naats"));
            group.count = playlist.ids.size();
            playlistGroups.add(group);
        }
    }

    private void showAddToPlaylist(final Song song) {
        final List<Playlists.Playlist> all = Playlists.all(this);
        final List<String> names = new ArrayList<>();
        for (Playlists.Playlist playlist : all) names.add(playlist.name);
        names.add("＋ New playlist…");

        new AlertDialog.Builder(this)
                .setTitle("Add to playlist")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == names.size() - 1) {
                            showCreatePlaylistDialog(song);
                            return;
                        }
                        String name = names.get(which);
                        boolean added = Playlists.addSong(MainActivity.this, name, song.id);
                        Toast.makeText(MainActivity.this,
                                added ? "Added to " + name : "Already in " + name,
                                Toast.LENGTH_SHORT).show();
                        refreshPlaylists();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCreatePlaylistDialog(final Song songToAdd) {
        final EditText input = new EditText(this);
        input.setHint("Playlist name");
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("New playlist")
                .setView(input)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) return;
                        if (!Playlists.create(MainActivity.this, name)) {
                            Toast.makeText(MainActivity.this, "Playlist already exists",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (songToAdd != null) Playlists.addSong(MainActivity.this, name, songToAdd.id);
                        refreshPlaylists();
                        Toast.makeText(MainActivity.this, "Playlist created", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPlaylistActions(final String name) {
        String[] actions = {"Open", "Rename", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            Intent intent = new Intent(MainActivity.this, PlaylistActivity.class);
                            intent.putExtra(PlaylistActivity.EXTRA_NAME, name);
                            startActivity(intent);
                        } else if (which == 1) {
                            showRenamePlaylistDialog(name);
                        } else {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Delete playlist?")
                                    .setMessage("\"" + name + "\" will be removed. Naats stay on your device.")
                                    .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Playlists.delete(MainActivity.this, name);
                                            refreshPlaylists();
                                        }
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        }
                    }
                })
                .show();
    }

    private void showRenamePlaylistDialog(final String name) {
        final EditText input = new EditText(this);
        input.setText(name);
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Rename playlist")
                .setView(input)
                .setPositiveButton("Rename", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String updated = input.getText().toString().trim();
                        if (Playlists.rename(MainActivity.this, name, updated)) {
                            refreshPlaylists();
                        } else {
                            Toast.makeText(MainActivity.this, "Name already used",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshPlaylists() {
        if (mode != MODE_PLAYLISTS) return;
        buildPlaylistGroups();
        if (groupAdapter != null) groupAdapter.notifyDataSetChanged();
        applyFilter(searchInput.getText().toString());
    }

    private void showAlbumActions(final GroupAdapter.Group group) {
        if (group.songs.isEmpty()) return;
        String[] items = {"Edit album…"};
        new AlertDialog.Builder(this)
                .setTitle(group.title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(MainActivity.this, TagsActivity.class);
                        intent.putExtra(TagsActivity.EXTRA_MODE, "album");
                        intent.putExtra(TagsActivity.EXTRA_ALBUM, group.title);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFolderActions(GroupAdapter.Group group) {
        if (group.songs.isEmpty()) return;
        final String path = group.songs.get(0).folder();
        final boolean hidden = Prefs.isFolderHidden(this, path);
        String name = group.title;

        new AlertDialog.Builder(this)
                .setTitle(name)
                .setMessage(path)
                .setPositiveButton(hidden ? "Unhide folder" : "Hide folder",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Prefs.setFolderHidden(MainActivity.this, path, !hidden);
                                toastAndReload(hidden ? "Folder unhidden" : "Folder hidden");
                            }
                        })
                .setNeutralButton("Open", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        folderFilter = path;
                        searchInput.setText("");
                        selectTab(TAB_SONGS);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toastAndReload(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        loadSongs();
    }

    // ------------------------------------------------------------------ player

    @Override
    public void onPlayerChanged() {
        paintTransport();
        if (songAdapter != null) {
            Song current = engine.getCurrent();
            songAdapter.setPlayingId(current == null ? -1 : current.id);
        }
    }

    private void paintTransport() {
        boolean playing = engine.isPlaying();
        btnPlay.setText(playing ? "❚❚" : "▶");
        Song current = engine.getCurrent();
        playerBar.setVisibility(current == null ? View.GONE : View.VISIBLE);
        if (current != null) {
            npTitle.setText(current.title);
            npArtist.setText(current.artist.isEmpty() ? current.album : current.artist);
            nowArt.setTag(current.id);
            int px = Math.round(40 * getResources().getDisplayMetrics().density);
            artLoader.load(current, px, nowArtImage, nowArtGlyph);
        } else {
            npTitle.setText(R.string.nothing_playing);
            npArtist.setText(R.string.tagline);
            npTime.setText("");
        }

    }

    private void openNowPlaying() {
        if (engine.getCurrent() != null) {
            startActivity(new Intent(MainActivity.this, NowPlayingActivity.class));
            overridePendingTransition(R.anim.np_in, R.anim.np_hold);
        }
    }

    private void updateProgress() {
        int duration = engine.duration();
        int position = engine.position();
        if (!dragging && duration > 0) {
            seekBar.setProgress((int) (1000L * position / duration));
        }
        if (engine.getCurrent() != null) {
            npTime.setText(formatTime(position) + " / " + formatTime(duration));
        }
    }

    public static String formatTime(long ms) {
        if (ms <= 0) return "0:00";
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }
}
