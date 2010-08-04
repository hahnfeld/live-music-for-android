package com.everysoft.livemusicforandroid;

import java.io.IOException;
import java.lang.reflect.Field;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.MediaController.MediaPlayerControl;

public class PlayerActivity extends ListActivity implements OnPreparedListener, OnCompletionListener {
	
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
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		this.setContentView(R.layout.player_main);
		mConcertId = getIntent().getExtras().getString("concert_id");
		mDb = new LiveMusicDbOpenHelper(this).getWritableDatabase();
		
		mCursor = mDb.query("songs", new String[] {"_id","identifier","title","song_length"}, "concert_id=?", new String[]{ mConcertId }, null, null, "identifier");
		mSongCount = mCursor.getCount();
		mCurrentSongPosition = 0;
		
		mAdapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2, mCursor, new String[] {"title","song_length"}, new int[] {android.R.id.text1, android.R.id.text2});
		this.setListAdapter(mAdapter);

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
		mMediaController.setAnchorView(findViewById(R.layout.player_main));
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
		
		// Title
        TextView tv = (TextView) findViewById(R.id.albumtitle);
        Cursor c = mDb.query("concerts", new String[]{ "title" }, "_id=?", new String[]{ mConcertId }, null, null, null);
		c.moveToFirst();
        tv.setText(c.getString(0));
		c.close();
        
		refreshIfTime();
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
		case R.id.about:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.about_message);
			builder.create().show();
			return true;
		case R.id.refresh:
			refreshSongs();
		default:
	        return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		mCurrentSongPosition = position;
		playCurrentSong();
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
	
	private void getSongsFromJSON() {
		Cursor c = mDb.query("concerts", new String[] { "identifier" }, "_id=?", new String[] { mConcertId }, null, null, null);
		c.moveToFirst();
		String concert = c.getString(0);
		c.close();
		JSONRetriever retriever = new JSONRetriever("http://www.archive.org/details/"+concert+"?output=json");
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
		// TODO: set icons
		if (mCursor.moveToPosition(mCurrentSongPosition)) {
			try {
				mMediaPlayer.setDataSource("http://www.archive.org/download/" + mCursor.getString(1));
				mMediaPlayer.prepareAsync();
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalStateException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void onPrepared(MediaPlayer mp) {
		mMediaPlayer.start();
		mMediaController.setEnabled(true);
		setShowing(true);
		mMediaController.show(); // start updating progress bar			
	}
	
	// A hack to work around an android bug which doesn't recognize the mediacontroller
	// as showing if it's embedded in XML.
	private void setShowing(Boolean showing) {
		try {
			Field ms = mMediaController.getClass().getDeclaredField("mShowing");
			ms.setAccessible(true);
			ms.set(mMediaController, showing);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void onCompletion(MediaPlayer mp) {
		next();
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