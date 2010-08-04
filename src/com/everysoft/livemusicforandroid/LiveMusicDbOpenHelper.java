package com.everysoft.livemusicforandroid;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class LiveMusicDbOpenHelper extends SQLiteOpenHelper {
	static String name = "livemusic"; // DB name
	static int version = 2; // DB Version
	
	public LiveMusicDbOpenHelper(Context context) {
		super(context, name, null, version);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE bands (" +
				"_id INTEGER PRIMARY KEY AUTOINCREMENT," +
				"identifier TEXT NOT NULL," +
				"title TEXT NOT NULL," +
				"updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
			");");
		db.execSQL("CREATE TABLE concerts (" +
				"_id INTEGER PRIMARY KEY AUTOINCREMENT," +
				"band_id INTEGER NOT NULL," +
				"identifier TEXT NOT NULL," +
				"title TEXT NOT NULL," +
				"rating STRING NOT NULL," +
				"updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
				"FOREIGN KEY (band_id) REFERENCES bands(_id) ON DELETE CASCADE" +
			");");
		db.execSQL("CREATE TABLE songs (" +
				"_id INTEGER PRIMARY KEY AUTOINCREMENT," +
				"concert_id INTEGER NOT NULL," +
				"identifier TEXT NOT NULL," +
				"title TEXT NOT NULL," +
				"song_length TEXT NOT NULL," +
				"updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
				"FOREIGN KEY (concert_id) REFERENCES concerts(_id) ON DELETE CASCADE" +
			");");
	}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}
}
