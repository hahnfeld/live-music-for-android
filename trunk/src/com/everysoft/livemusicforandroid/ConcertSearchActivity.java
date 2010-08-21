package com.everysoft.livemusicforandroid;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.AbsListView;
import android.widget.RatingBar;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class ConcertSearchActivity extends ListActivity implements SimpleCursorAdapter.ViewBinder, AbsListView.OnScrollListener {
	
	SQLiteDatabase mDb;
	String mQueryString;
	ProgressDialog mDialog;
	Cursor mCursor;
	SimpleCursorAdapter mAdapter;
	final Handler mHandler = new Handler();
	String mDeepError;
	Context mContext = this;
	int mPageNum = 1;
	int mLastTotalCount = 0;
	String mSort = "";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		this.setContentView(R.layout.list);
		mDb = new LiveMusicDbOpenHelper(this).getWritableDatabase();
		
		if (savedInstanceState != null) {
			mQueryString = savedInstanceState.getString("query_string");
		} else if (getIntent().getExtras().containsKey(SearchManager.QUERY)) {
			mQueryString = getIntent().getStringExtra(SearchManager.QUERY);
		} else if (getIntent().getExtras().containsKey(SearchManager.EXTRA_DATA_KEY)) {
			SQLiteStatement stmt = mDb.compileStatement("SELECT identifier FROM bands WHERE _id=?");
			stmt.bindString(1, getIntent().getStringExtra(SearchManager.EXTRA_DATA_KEY));
			mQueryString = "collection:(" + stmt.simpleQueryForString() + ")";
			stmt.close();
			mSort = "date+desc"; // if we're searching a collection, sort by date
		} else {
			mQueryString = "";
		}
		
		mCursor = mDb.query("concerts c, bands b", new String[] {"c._id","b.title || ' Live' band","'at ' || c.location location","c.concert_date","c.rating"}, "c.search_flag=1 and b._id = c.band_id", null, null, null, "c._id"); // sorted on insert
		mAdapter = new SimpleCursorAdapter(this, R.layout.concert_list_item, mCursor, new String[] {"band", "location", "concert_date", "rating"}, new int[] {R.id.line1, R.id.line2, R.id.duration, R.id.ratingbar});
		this.setListAdapter(mAdapter);
		mAdapter.setViewBinder(this);
		getListView().setOnScrollListener(this);
		
		resetSearch();
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("query_string", mQueryString);
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
		case R.id.refresh:
			resetSearch();
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

	@Override
    public void onScroll(AbsListView view, int firstVisible, int visibleCount, int totalCount) {
		if (totalCount >= 100 && (firstVisible + visibleCount) >= totalCount && totalCount != mLastTotalCount) {
			mLastTotalCount = totalCount;
			getMoreResults();
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		// Do nothing!
	}
	
	public void resetSearch() {
		mDb.delete("concerts", "search_flag=1", null);
		mPageNum = 1;
		getMoreResults();
	}
	
	public void getMoreResults() {
		mDialog = ProgressDialog.show(this, "", "Getting search results...", true);
        Thread t = new Thread() {
            public void run() {
                getSearchResultsFromJSON();
                mHandler.post(mGotMoreResults);
            }
        };
        t.start();
	}
	
	private void getSearchResultsFromJSON() {
		JSONRetriever retriever = new JSONRetriever("http://www.archive.org/advancedsearch.php?q="+ Uri.encode(mQueryString) +"+AND+mediatype%3A%28etree%29&fl[]=collection,identifier,title,avg_rating&rows=100&page=" + mPageNum + "&sort[]=" + mSort + "&output=json");
		SQLiteStatement stmt = mDb.compileStatement("INSERT INTO concerts (band_id,identifier,location,concert_date,rating,search_flag) SELECT _id, ?, ?, ?, ?, 1 FROM bands where identifier=?");
		mDb.beginTransaction();
		try {
			JSONArray concerts = retriever.getJSON().getJSONObject("response").getJSONArray("docs");
			for (int i=0; i<concerts.length(); i++) {
				JSONObject concert = concerts.getJSONObject(i);
				stmt.bindString(1, concert.getString("identifier"));
				
				String title = concert.getString("title");
				int live_at_index = title.indexOf("Live at");
				if (live_at_index >= 0) {
					String loc_date = title.substring(live_at_index + 8);
					if (loc_date.length() > 14 && loc_date.lastIndexOf("on") == loc_date.length() - 13) {
						stmt.bindString(2, loc_date.substring(0, loc_date.length() - 14)); // location
						stmt.bindString(3, loc_date.substring(loc_date.length() - 10)); // date						
					}
					else {
						continue;
					}
				}
				else {
					continue;
				}
				
				if (concert.has("avg_rating")) {
					stmt.bindString(4, concert.getString("avg_rating"));
				} else {
					stmt.bindString(4, "0.0");
				}
				
				stmt.bindString(5, concert.getJSONArray("collection").getString(0));
				
				stmt.execute();
			}
			mDb.setTransactionSuccessful();
			mPageNum++;
			mDeepError = null;
		} catch (Exception e) {
			mDeepError = "Cannot retrieve data from archive.org.  Check your connection!";
			e.printStackTrace();
		}
		mDb.endTransaction();
		stmt.close();
	}
	
	final Runnable mGotMoreResults = new Runnable() {
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