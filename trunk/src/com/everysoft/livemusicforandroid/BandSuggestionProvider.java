package com.everysoft.livemusicforandroid;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

public class BandSuggestionProvider extends ContentProvider {

	SQLiteDatabase mDb;
	
	@Override
	public int delete(Uri arg0, String arg1, String[] arg2) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getType(Uri uri) {
		return SearchManager.SUGGEST_MIME_TYPE;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean onCreate() {
		mDb = new LiveMusicDbOpenHelper(this.getContext()).getReadableDatabase();
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		return mDb.query("bands", new String[] {"_id", "'Band: ' || title " + SearchManager.SUGGEST_COLUMN_TEXT_1, "_id " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA}, "title LIKE '%' || ? || '%'", new String[] { uri.getLastPathSegment() }, null, null, "LOWER(identifier)");
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		throw new UnsupportedOperationException();
	}

}
