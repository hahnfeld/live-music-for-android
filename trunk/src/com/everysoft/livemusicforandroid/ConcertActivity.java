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
import android.database.sqlite.SQLiteOpenHelper;
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
	
	SQLiteDatabase concertsReadDb;
	String mCollection;
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
		mCollection = getIntent().getExtras().getString("collection");
		concertsReadDb = new ConcertsDbOpenHelper(this).getReadableDatabase();
		mCursor = concertsReadDb.query("concerts", new String[] {"_id","title","rating"}, "collection=?", new String[]{ mCollection }, null, null, "_id"); // sorted on insert
		mAdapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2, mCursor, new String[] {"title","rating"}, new int[] {android.R.id.text1, android.R.id.text2});
		this.setListAdapter(mAdapter);
		this.getListView().setFastScrollEnabled(true);
		
		refreshIfTime();
	}
	
	@Override
	public void onDestroy() {
		mCursor.close();
		concertsReadDb.close();
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
		Cursor c = concertsReadDb.query("concerts", new String[]{ "concert" }, "_id=?", new String[]{ ""+id }, null, null, null);
		if (c.isAfterLast()) {
			Toast.makeText(this, "Unavailable.", Toast.LENGTH_SHORT).show();
		}
		else {
			c.moveToFirst();
			String concert = c.getString(0);
			
			Intent playerActivityIntent = new Intent(this, PlayerActivity.class);
			playerActivityIntent.putExtra("concert", concert);
			this.startActivity(playerActivityIntent);
		}
		c.close();
	}
	
	public void refreshIfTime() {
		SQLiteDatabase concertsDb = new ConcertsDbOpenHelper(this).getWritableDatabase();
		concertsDb.delete("concerts", "collection=? AND updated < datetime('now','-1 day')", new String[] { mCollection });
		concertsDb.close();
		
		Cursor c = concertsReadDb.rawQuery("SELECT * FROM concerts WHERE collection=? LIMIT 1", new String[] { mCollection });
		if (c.isAfterLast()) {
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
		SQLiteDatabase concertsDb = new ConcertsDbOpenHelper(this).getWritableDatabase();
		JSONRetriever retriever = new JSONRetriever("http://www.archive.org/advancedsearch.php?q=collection%3A%28"+mCollection+"%29+AND+mediatype%3A%28etree%29&fl[]=identifier&fl[]=avg_rating&fl[]=title&sort[]=date+desc&rows=50000&output=json");
		SQLiteStatement stmt = concertsDb.compileStatement("INSERT INTO concerts (collection,concert,title,rating) VALUES (?, ?, ?, ?)");
		concertsDb.beginTransaction();
		concertsDb.delete("concerts", "collection=?", new String[]{ mCollection });
		try {
			JSONArray concerts = retriever.getJSON().getJSONObject("response").getJSONArray("docs");
			for (int i=0; i<concerts.length(); i++) {
				JSONObject concert = concerts.getJSONObject(i);
				stmt.bindString(1, mCollection);
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
			concertsDb.setTransactionSuccessful();
			mDeepError = null;
		} catch (Exception e) {
			mDeepError = "Cannot retrieve data from archive.org.  Check your connection!";
			e.printStackTrace();
		}
		concertsDb.endTransaction();
		concertsDb.close();
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

class ConcertsDbOpenHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "concerts";
    private static final int DATABASE_VERSION = 2;
    private static final String CONCERTS_TABLE_NAME = "concerts";
    private static final String CONCERTS_TABLE_CREATE =
    	"CREATE TABLE " + CONCERTS_TABLE_NAME + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, collection TEXT NOT NULL, concert TEXT NOT NULL, title TEXT NOT NULL, rating STRING NOT NULL, updated TIMESTAMP);";

    ConcertsDbOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CONCERTS_TABLE_CREATE);
    }

	@Override
	public void onUpgrade(SQLiteDatabase db, int ver1, int ver2) {
	}
}