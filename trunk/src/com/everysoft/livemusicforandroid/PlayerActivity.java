package com.everysoft.livemusicforandroid;

import java.lang.reflect.Field;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.MediaController.MediaPlayerControl;

public class PlayerActivity extends ListActivity implements OnPreparedListener, OnCompletionListener, SimpleCursorAdapter.ViewBinder {
	
	SQLiteDatabase mDb;
	String mConcertId;
	ProgressDialog mDialog;
	Cursor mCursor;
	SimpleCursorAdapter mAdapter;
	final Handler mHandler = new Handler();
	String mDeepError;
	Context mContext = this;
	
	MediaPlayer mMediaPlayer;
	MPlayerControl mMediaPlayerControl;
	MediaController mMediaController;
	
	int mSongCount;
	int mCurrentSongPosition;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Set up the activity
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		this.setContentView(R.layout.player_list);
		
		// Get the concert ID
		if (savedInstanceState != null) {
			mConcertId = savedInstanceState.getString("concert_id");
		} else {
			mConcertId = getIntent().getExtras().getString("concert_id");
		}
		
		// Create a database handle
		mDb = new LiveMusicDbOpenHelper(this).getWritableDatabase();
		
		// Title
        TextView tv = (TextView) findViewById(R.id.albumtitle);
        Cursor c = mDb.query("concerts c, bands b", new String[]{ "b.title || ' Live at ' || c.location || ' on ' || c.concert_date title" }, "c._id=? and b._id = c.band_id", new String[]{ mConcertId }, null, null, null);
		c.moveToFirst();
        tv.setText(c.getString(0));
		c.close();

		// Clear any playing flags
		ContentValues cv = new ContentValues();
		cv.put("play_icon", 0);
		mDb.update("songs", cv, null, null);
		
		// Cursor for the List
		mCursor = mDb.query("songs s, concerts c, bands b", new String[] {"s._id","s.identifier","s.title AS song_title","s.song_length","s.play_icon","b.title AS band_title"}, "s.concert_id=? AND c._id=s.concert_id AND b._id=c.band_id", new String[]{ mConcertId }, null, null, "s.identifier");
		mSongCount = mCursor.getCount();
		mCurrentSongPosition = 0;
		
		// List Adapter
		mAdapter = new SimpleCursorAdapter(this, R.layout.player_list_item, mCursor, new String[] {"song_title","song_length","play_icon","band_title"}, new int[] {R.id.line1, R.id.duration, R.id.play_indicator, R.id.line2});
		setListAdapter(mAdapter);
		mAdapter.setViewBinder(this);

		// MediaPlayer
		mMediaPlayer = new MediaPlayer();
		mMediaPlayer.setOnPreparedListener(this);
		mMediaPlayer.setOnCompletionListener(this);

		// MediaPlayerControl
		mMediaPlayerControl = new MPlayerControl(mMediaPlayer);

		// Set up buffering support
		mMediaPlayer.setOnBufferingUpdateListener(mMediaPlayerControl);

		// MediaController
		mMediaController = (MediaController) findViewById(R.id.mediacontroller);
		mMediaController.setAnchorView(findViewById(R.layout.player_list));
		mMediaController.setMediaPlayer(mMediaPlayerControl);
		mMediaController.setPrevNextListeners(
				new View.OnClickListener() {
					public void onClick(View v) {
						next();
					}
				},
				new View.OnClickListener() {
					public void onClick(View v) {
						prev();
					}
				});
		mMediaController.setEnabled(false);
		        
		// Refresh
		refreshIfTime();
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("concert_id", mConcertId);
		super.onSaveInstanceState(outState);
	}
	
	@Override
	public void onDestroy() {
		mCursor.close();
		mDb.close();
		
		if (mMediaPlayer.isPlaying()) {
			mMediaPlayer.stop();
		}
		this.mMediaPlayerControl.killMediaPlayer(); // TODO: ugly!
		mMediaPlayer.release();
		mMediaPlayer = null;
		super.onDestroy();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.player_menu, menu);
	    return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.refresh:
			refreshSongs();
		case R.id.details:
			Intent browserIntent = new Intent("android.intent.action.VIEW", Uri.parse("http://www.archive.org/details/"+getConcertIdentifier()));
			startActivity(browserIntent);
		default:
	        return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		mCurrentSongPosition = position;
		playCurrentSong();
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		// Icon
		ContentValues cv = new ContentValues();
		cv.put("play_icon", R.drawable.indicator_ic_mp_playing_list);
		mDb.update("songs", cv, "play_icon=?", new String[] { Integer.toString(R.drawable.ic_spinner) });
		mCursor.requery();
		mAdapter.notifyDataSetChanged();

		// Play
		mMediaPlayer.start();
		mMediaController.setEnabled(true);
		setShowing(true);
		mMediaController.show(); // start updating progress bar			
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		next();
	}
	
	// Handle Animations by Setting Image Backgrounds
	@Override
	public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
		if (view instanceof ImageView) {
			ImageView imageView = (ImageView) view;
			String value = cursor.getString(columnIndex);
			// Note: URIs are *not* supported!
	        imageView.setBackgroundResource(Integer.parseInt(value));
	        
	        Drawable drawable = imageView.getBackground();
	        if (drawable instanceof AnimationDrawable) {
	        	AnimationDrawable ad = (AnimationDrawable) drawable;
	        	ad.start();
	        }
	        return true;
		}
		return false;
	}
	
	public void refreshIfTime() {
		Cursor c = mDb.query("songs", new String[] { "count(*)" }, "concert_id=? AND updated > datetime('now','-1 day')", new String[] { mConcertId }, null, null, null);
		c.moveToFirst();
		if (c.getInt(0) == 0) {
			refreshSongs();
		}
		c.close();
	}
	
	public void refreshSongs() {
		mDialog = ProgressDialog.show(this, "", "Updating. Please wait...", true);
        Thread t = new Thread() {
            public void run() {
                getSongsFromJSON();
                mHandler.post(mFinishRefreshSongs);
            }
        };
        t.start();
	}
	
	private String getConcertIdentifier() {
		SQLiteStatement stmt = mDb.compileStatement("SELECT identifier FROM concerts WHERE _id=?");
		stmt.bindString(1, mConcertId);
		String identifier = stmt.simpleQueryForString();
		stmt.close();
		return identifier;		
	}
	
	private void getSongsFromJSON() {
		JSONRetriever retriever = new JSONRetriever("http://www.archive.org/details/"+getConcertIdentifier()+"?output=json");
		SQLiteStatement stmt = mDb.compileStatement("INSERT INTO songs (concert_id,identifier,title,song_length) VALUES (?, ?, ?, ?)");
		mDb.beginTransaction();
		mDb.delete("songs", "concert_id=?", new String[]{ mConcertId });
		try {
			JSONObject response = retriever.getJSON();
			JSONObject metadata = response.getJSONObject("metadata");
			JSONObject files = response.getJSONObject("files");

			JSONArray names = files.names();
			for (int i = 0; i<names.length(); i++) {
				String identifier = names.getString(i);
				JSONObject fileInfo = files.getJSONObject(identifier);

				if (! fileInfo.has("format") || !fileInfo.getString("format").equals("VBR MP3")) {
					continue;
				}

				stmt.bindString(1, mConcertId);
				stmt.bindString(2, metadata.getJSONArray("identifier").getString(0) + identifier);
				stmt.bindString(3, fileInfo.has("title") ? fileInfo.getString("title") : "Unknown Title");
				stmt.bindString(4, fileInfo.has("length") ? fileInfo.getString("length") : "Unknown Length");
				stmt.execute();
			}
			mDb.setTransactionSuccessful();
			mDeepError = null;
		} catch (Exception e) {
			mDeepError = "Cannot retrieve data from archive.org.  Check your connection!";
			e.printStackTrace();
		}
		mDb.endTransaction();
	}
	
	final Runnable mFinishRefreshSongs = new Runnable() {
        public void run() {
        	if (mDeepError != null) {
        		Toast.makeText(mContext, mDeepError, Toast.LENGTH_LONG).show();
        	}
        	else {
        		mCursor.requery();
        		mSongCount = mCursor.getCount();
        		mAdapter.notifyDataSetChanged();
        	}
            mDialog.dismiss();
        }
    };
    
	private void playCurrentSong() {
		if (mMediaPlayer.isPlaying()) {
			mMediaPlayer.stop();
		}
		mMediaPlayer.reset();
		if (mCursor.moveToPosition(mCurrentSongPosition)) {
			// Set Data Source
			try {
				mMediaPlayer.setDataSource("http://www.archive.org/download/" + mCursor.getString(1));
				mMediaPlayer.prepareAsync();
			} catch (Exception e) {
        		Toast.makeText(mContext, "Unable to load remote stream.  Try again later.", Toast.LENGTH_LONG).show();
			}

			// Icon
			ContentValues cv = new ContentValues();
			cv.put("play_icon", 0);
			mDb.update("songs", cv, null, null);
			cv.put("play_icon", R.drawable.ic_spinner);
			mDb.update("songs", cv, "_id=?", new String[] { mCursor.getString(0) });
			mCursor.requery();
			mAdapter.notifyDataSetChanged();
		}
	}
	
	// A hack to work around an android bug which doesn't recognize the mediacontroller
	// as showing if it's embedded in XML.
	private void setShowing(Boolean showing) {
		try {
			Field ms = mMediaController.getClass().getDeclaredField("mShowing");
			ms.setAccessible(true);
			ms.set(mMediaController, showing);
		} catch (Exception e) {
			e.printStackTrace(); // This shouldn't happen; log it!
		}
	}
	
	private void next() {
		if (mCurrentSongPosition < (mSongCount-1)) {
			mCurrentSongPosition++;
			playCurrentSong();
		}
	}

	private void prev() {
		if (mCurrentSongPosition > 0) {
			mCurrentSongPosition--;
		}
		playCurrentSong();
	}
}

class MPlayerControl implements MediaPlayerControl, MediaPlayer.OnBufferingUpdateListener {
	int percent;
	MediaPlayer mMediaPlayer;

	public MPlayerControl(MediaPlayer player) {
		mMediaPlayer = player;
	}
	
	public void killMediaPlayer() {
		mMediaPlayer = null;
	}

	@Override
	public boolean canPause() {
		return true;
	}

	@Override
	public boolean canSeekBackward() {
		return true;
	}

	@Override
	public boolean canSeekForward() {
		return true;
	}

	@Override
	public int getBufferPercentage() {
		if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
			return percent;
		}
		return 0;
	}

	@Override
	public int getCurrentPosition() {
		if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
			return mMediaPlayer.getCurrentPosition();
		}
		return 0;
	}

	@Override
	public int getDuration() {
		if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
			return mMediaPlayer.getDuration();
		}
		return 0;
	}

	@Override
	public boolean isPlaying() {
		if (mMediaPlayer != null) {
			return mMediaPlayer.isPlaying();
		}
		return false;
	}

	@Override
	public void pause() {
		if (mMediaPlayer != null) {
			mMediaPlayer.pause();
		}
	}

	@Override
	public void seekTo(int pos) {
		if (mMediaPlayer != null) {
			mMediaPlayer.seekTo(pos);
		}
	}

	@Override
	public void start() {
		if (mMediaPlayer != null) {
			mMediaPlayer.start();
		}
	}

	@Override
	public void onBufferingUpdate(MediaPlayer mp, int percent) {
		this.percent = percent;
	}
}