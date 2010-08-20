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
import android.widget.RatingBar;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class ConcertActivity extends ListActivity implements SimpleCursorAdapter.ViewBinder {
	
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
		mCursor = mDb.query("concerts c, bands b", new String[] {"c._id","b.title || ' Live' band","'at ' || c.location location","c.concert_date","c.rating"}, "c.band_id=? and b._id = c.band_id", new String[]{ mBandId }, null, null, "c._id"); // sorted on insert
		mAdapter = new SimpleCursorAdapter(this, R.layout.concert_list_item, mCursor, new String[] {"band", "location", "concert_date", "rating"}, new int[] {R.id.line1, R.id.line2, R.id.duration, R.id.ratingbar});
		this.setListAdapter(mAdapter);
		mAdapter.setViewBinder(this);
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

	// Handle Ratings
	@Override
	public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
		if (view instanceof RatingBar) {
			RatingBar ratingBar = (RatingBar) view;
			Float rating = cursor.getFloat(columnIndex);
			ratingBar.setRating(rating);
	        return true;
		}
		return false;
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
		SQLiteStatement stmt = mDb.compileStatement("INSERT INTO concerts (band_id,identifier,location,concert_date,rating) VALUES (?, ?, ?, ?, ?)");
		mDb.beginTransaction();
		mDb.delete("concerts", "band_id=?", new String[]{ mBandId });
		try {
			JSONArray concerts = retriever.getJSON().getJSONObject("response").getJSONArray("docs");
			for (int i=0; i<concerts.length(); i++) {
				JSONObject concert = concerts.getJSONObject(i);
				stmt.bindString(1, mBandId);
				stmt.bindString(2, concert.getString("identifier"));
				
				String title = concert.getString("title");
				String[] split_title = title.split(" Live at ", 2);
				if (split_title[1] != null && split_title[1].length() > 14) {
					stmt.bindString(3, split_title[1].substring(0, split_title[1].length() - 14)); // location
					stmt.bindString(4, split_title[1].substring(split_title[1].length() - 10)); // date
				}
				
				if (concert.has("avg_rating")) {
					stmt.bindString(5, concert.getString("avg_rating"));
				} else {
					stmt.bindString(5, "0.0");
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