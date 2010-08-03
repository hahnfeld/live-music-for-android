package com.everysoft.livemusicforandroid;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.AlphabetIndexer;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class BandActivity extends ListActivity {
	
	SQLiteDatabase bandsReadDb;
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
		bandsReadDb = new BandsDbOpenHelper(this).getReadableDatabase();
		mCursor = bandsReadDb.query("bands", new String[] {"_id","title"}, null, null, null, null, "title");
		mAdapter = new BandsAdapter(this, android.R.layout.simple_list_item_1, mCursor, new String[] {"title"}, new int[] {android.R.id.text1});
		this.setListAdapter(mAdapter);
		this.getListView().setFastScrollEnabled(true);
		
		refreshIfTime();
	}

	@Override
	public void onDestroy() {
		mCursor.close();
		bandsReadDb.close();
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.band_menu, menu);
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
			refreshBands();
		default:
	        return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Cursor c = bandsReadDb.query("bands", new String[]{ "collection" }, "_id=?", new String[]{ ""+id }, null, null, null);
		if (c.isAfterLast()) {
			Toast.makeText(this, "Unavailable.", Toast.LENGTH_SHORT);
		}
		else {
			c.moveToFirst();
			String collection = c.getString(0);
			
			Intent concertActivityIntent = new Intent(this, ConcertActivity.class);
			concertActivityIntent.putExtra("collection", collection);
			this.startActivity(concertActivityIntent);
		}
		c.close();
	}
	
	public void refreshIfTime() {
		SharedPreferences pref = this.getPreferences(MODE_PRIVATE);
		Long now = System.currentTimeMillis();
		if (now - pref.getLong("refreshed", 0) > 86400000) {
			SharedPreferences.Editor e = pref.edit();
			e.putLong("refreshed", now);
			e.commit();
			refreshBands();
		}
	}
	
	public void refreshBands() {
		mDialog = ProgressDialog.show(this, "", "Updating. Please wait...", true);
        Thread t = new Thread() {
            public void run() {
                getBandsFromJSON();
                mHandler.post(mFinishRefreshBands);
            }
        };
        t.start();
	}
	
	private void getBandsFromJSON() {
		SQLiteDatabase bandsDb = new BandsDbOpenHelper(this).getWritableDatabase();
		JSONRetriever retriever = new JSONRetriever("http://www.archive.org/advancedsearch.php?q=collection%3A%28etree%29+AND+mediatype%3A%28collection%29&fl[]=identifier&fl[]=title&rows=10000&output=json");
		SQLiteStatement stmt = bandsDb.compileStatement("INSERT INTO bands (collection,title) VALUES (?, ?)");
		bandsDb.beginTransaction();
		bandsDb.delete("bands", null, null);
		try {
			JSONArray collections = retriever.getJSON().getJSONObject("response").getJSONArray("docs");
			for (int i=0; i<collections.length(); i++) {
				JSONObject collection = collections.getJSONObject(i);
				stmt.bindString(1, collection.getString("identifier"));
				stmt.bindString(2, collection.getString("title"));
				stmt.execute();
			}
			bandsDb.setTransactionSuccessful();
			mDeepError = null;
		} catch (Exception e) {
			mDeepError = "Cannot retrieve data from archive.org.  Check your connection!";
			e.printStackTrace();
		}
		bandsDb.endTransaction();
		bandsDb.close();
	}
	
	final Runnable mFinishRefreshBands = new Runnable() {
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

class BandsDbOpenHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "bands";
    private static final int DATABASE_VERSION = 2;
    private static final String BANDS_TABLE_NAME = "bands";
    private static final String BANDS_TABLE_CREATE =
    	"CREATE TABLE " + BANDS_TABLE_NAME + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, collection TEXT NOT NULL, title TEXT NOT NULL);";

    BandsDbOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(BANDS_TABLE_CREATE);
    }

	@Override
	public void onUpgrade(SQLiteDatabase db, int ver1, int ver2) {
	}
}

class BandsAdapter extends SimpleCursorAdapter implements SectionIndexer{
	AlphabetIndexer mIndexer;
	
	public BandsAdapter(Context context, int layout, Cursor c, String[] from,
			int[] to) {
		super(context, layout, c, from, to);
		mIndexer = new AlphabetIndexer(c, c.getColumnIndex("title"), " 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ");
	}

	@Override
	public int getPositionForSection(int section) {
		return mIndexer.getPositionForSection(section);
	}

	@Override
	public int getSectionForPosition(int position) {
		return mIndexer.getSectionForPosition(position);
	}

	@Override
	public Object[] getSections() {
		return mIndexer.getSections();
	}
}