package com.everysoft.livemusicforandroid;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class ConcertActivity extends ListActivity {
	
	SQLiteDatabase mDb;
	String mBandId;
	ProgressDialog mDialog;
	Cursor mCursor;
	SimpleCursorAdapter mAdapter;
	final Handler mHandler = new Handler();
	String mDeepError;
	Context mContext = this;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		this.setContentView(R.layout.list);
		
		if (savedInstanceState != null) {
			mBandId = savedInstanceState.getString("band_id");
		} else {
			mBandId = getIntent().getExtras().getString("band_id");
		}

		mDb = new LiveMusicDbOpenHelper(this).getWritableDatabase();
		mCursor = mDb.query("concerts", new String[] {"_id","title","rating"}, "band_id=?", new String[]{ mBandId }, null, null, "_id"); // sorted on insert
		mAdapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2, mCursor, new String[] {"title", "rating"}, new int[] {android.R.id.text1, android.R.id.text2});
		this.setListAdapter(mAdapter);
		this.getListView().setFastScrollEnabled(true);
		
		refreshIfTime();
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("band_id", mBandId);
		super.onSaveInstanceState(outState);
	}
	
	@Override
	public void onDestroy() {
		mCursor.close();
		mDb.close();
		super.onDestroy();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.concert_menu, menu);
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
			refreshConcerts();
		default:
	        return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Intent playerActivityIntent = new Intent(this, PlayerActivity.class);
		playerActivityIntent.putExtra("concert_id", ""+id);
		this.startActivity(playerActivityIntent);
	}
	
	public void refreshIfTime() {
		Cursor c = mDb.query("concerts", new String[] { "count(*)" }, "band_id=? AND updated > datetime('now','-1 day')", new String[] { mBandId }, null, null, null);
		c.moveToFirst();
		if (c.getInt(0) == 0) {
			refreshConcerts();
		}
		c.close();
	}
	
	public void refreshConcerts() {
		mDialog = ProgressDialog.show(this, "", "Updating. Please wait...", true);
        Thread t = new Thread() {
            public void run() {
                getConcertsFromJSON();
                mHandler.post(mFinishRefreshConcerts);
            }
        };
        t.start();
	}
	
	private void getConcertsFromJSON() {
		Cursor c = mDb.query("bands", new String[] { "identifier" }, "_id=?", new String[] { mBandId }, null, null, null);
		c.moveToFirst();
		String collection = c.getString(0);
		c.close();
		JSONRetriever retriever = new JSONRetriever("http://www.archive.org/advancedsearch.php?q=collection%3A%28"+collection+"%29+AND+mediatype%3A%28etree%29&fl[]=identifier&fl[]=title&fl[]=avg_rating&sort[]=date+desc&rows=50000&output=json");
		SQLiteStatement stmt = mDb.compileStatement("INSERT INTO concerts (band_id,identifier,title,rating) VALUES (?, ?, ?, ?)");
		mDb.beginTransaction();
		mDb.delete("concerts", "band_id=?", new String[]{ mBandId });
		try {
			JSONArray concerts = retriever.getJSON().getJSONObject("response").getJSONArray("docs");
			for (int i=0; i<concerts.length(); i++) {
				JSONObject concert = concerts.getJSONObject(i);
				stmt.bindString(1, mBandId);
				stmt.bindString(2, concert.getString("identifier"));
				stmt.bindString(3, concert.getString("title"));
				if (concert.has("avg_rating")) {
					Double stars = concert.getDouble("avg_rating");
					if (stars < 0.5) { stmt.bindString(4, "Rating: 0/5"); }
					else if (stars < 1.0) { stmt.bindString(4, "Rating: 0.5/5"); }
					else if (stars < 1.5) { stmt.bindString(4, "Rating: 1/5"); }
					else if (stars < 2.0) { stmt.bindString(4, "Rating: 1.5/5"); }
					else if (stars < 2.5) { stmt.bindString(4, "Rating: 2/5"); }
					else if (stars < 3.0) { stmt.bindString(4, "Rating: 2.5/5"); }
					else if (stars < 3.5) { stmt.bindString(4, "Rating: 3/5"); }
					else if (stars < 4.0) { stmt.bindString(4, "Rating: 3.5/5"); }
					else if (stars < 4.5) { stmt.bindString(4, "Rating: 4/5"); }
					else if (stars < 5.0) { stmt.bindString(4, "Rating: 4.5/5"); }
					else { stmt.bindString(4, "Rating: 5/5"); }
				}
				else {
					stmt.bindString(4, "Not Rated Yet");
				}
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
	
	final Runnable mFinishRefreshConcerts = new Runnable() {
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
}