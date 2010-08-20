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
				"location STRING NOT NULL DEFAULT 'Unknown'," +
				"concert_date STRING NOT NULL DEFAULT 'Unknown'," +
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
				"play_icon INTEGER NOT NULL DEFAULT 0," +
				"updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
				"FOREIGN KEY (concert_id) REFERENCES concerts(_id) ON DELETE CASCADE" +
			");");
	}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion <= 1) {
			db.execSQL("DROP TABLE concerts;");
			db.execSQL("DROP TABLE bands;");
			onCreate(db);
		}
		else if (oldVersion == 2) {
			db.execSQL("DROP TABLE concerts;");
			db.execSQL("DROP TABLE bands;");
			db.execSQL("DROP TABLE songs;");
			onCreate(db);
		}
	}
}
