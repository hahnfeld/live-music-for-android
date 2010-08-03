package com.everysoft.livemusicforandroid;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.MediaController.MediaPlayerControl;

public class PlayerActivity extends ListActivity implements OnPreparedListener, OnCompletionListener {
	List<Map<String, Object>> mSongData;
	String mConcert;
	MediaPlayer mMediaPlayer;
	MPlayerControl mMediaPlayerControl;
	MediaController mMediaController;
	int mCurPosition;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mConcert = getIntent().getExtras().getString("concert");
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		setContentView(R.layout.player_main);
		// Initialize the current position
		mCurPosition = 0;

		// ListView
		mSongData = new ArrayList<Map<String, Object>>();
		populateSongData();
		SimpleAdapter adapter = new SimpleAdapter(this, mSongData,
				R.layout.player_row,
				new String[] { "ICON", "TITLE", "TIME" }, new int[] { R.id.icon, R.id.firstLine, R.id.secondLine });
		setListAdapter(adapter);

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
	}
	
	@Override
	public void onDestroy() {
		if (mMediaPlayer.isPlaying()) {
			mMediaPlayer.stop();
		}
		this.mMediaPlayerControl.killMediaPlayer();
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
		default:
	        return super.onOptionsItemSelected(item);
		}
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

	private void populateSongData() {
		String jsonUrl = "http://www.archive.org/details/"+mConcert+"?output=json";
		String downloadUrl = "http://www.archive.org/download/"+mConcert;

		try {
			JSONRetriever retriever = new JSONRetriever(jsonUrl);
			JSONObject received = retriever.getJSON();
			
			TextView tv = (TextView) findViewById(R.id.albumtitle);
			JSONObject metadata = received.getJSONObject("metadata");
			if (metadata.has("title")){
				tv.setText(metadata.getJSONArray("title").getString(0));
			}
			else {
				tv.setText("No Title");
			}

			JSONObject files = received.getJSONObject("files");
			JSONArray names = files.names();
			for (int i = 0; i<names.length(); i++) {
				Map<String,Object> newSong= new HashMap<String,Object>();
				String url = names.getString(i);
				JSONObject fileInfo = files.getJSONObject(url);

				if (! fileInfo.has("format") || !fileInfo.getString("format").equals("VBR MP3")) {
					continue;
				}

				newSong.put("TITLE", fileInfo.has("title") ? fileInfo.getString("title") : "Unknown Title");
				newSong.put("TIME", fileInfo.has("length") ? fileInfo.getString("length") : "Unknown Length");
				newSong.put("URL", downloadUrl + url);
				newSong.put("ICON", R.drawable.ic_media_play_off);

				mSongData.add(newSong);
			}
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// someone clicks on a list item
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		mCurPosition = position;
		playCurrentSong();
	}

	// play new song
	private void playCurrentSong() {
		try {
			if (mMediaPlayer.isPlaying()) {
				mMediaPlayer.stop();
			}
			
			// Set icons
			for (Map<String, Object> sd:mSongData) {
				sd.put("ICON", R.drawable.ic_media_play_off);
			}
			mSongData.get(mCurPosition).put("ICON", android.R.drawable.ic_media_play);
			((SimpleAdapter) getListAdapter()).notifyDataSetChanged();

			mMediaPlayer.reset();
			mMediaPlayer.setDataSource((String) mSongData.get(mCurPosition).get("URL"));
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

	// file is prepared
	@Override
	public void onPrepared(MediaPlayer mp) {
		mMediaPlayer.start();
		mMediaController.setEnabled(true);
		setShowing(true);
		mSongData.get(mCurPosition).put("ICON", android.R.drawable.ic_media_play);
		((SimpleAdapter) getListAdapter()).notifyDataSetChanged();
		mMediaController.show(); // start updating progress bar			
	}

	// file completes playing
	@Override
	public void onCompletion(MediaPlayer mp) {
		next();
	}

	private void next() {
		if (mCurPosition < (mSongData.size()-1)) {
			mCurPosition++;
			playCurrentSong();
		}
	}

	private void prev() {
		if (mCurPosition > 0) {
			mCurPosition--;
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