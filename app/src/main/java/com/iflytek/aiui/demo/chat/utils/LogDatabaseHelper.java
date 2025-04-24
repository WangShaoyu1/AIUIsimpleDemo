package com.iflytek.aiui.demo.chat.utils;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class LogDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "aiui_logs.db";
    private static final int DATABASE_VERSION = 1;
    public static final String TABLE_NAME = "logs";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_MODULE = "module";
    public static final String COLUMN_TAG = "tag";
    public static final String COLUMN_MESSAGE = "message";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_MODULE + " TEXT, " +
            COLUMN_TAG + " TEXT, " +
            COLUMN_MESSAGE + " TEXT, " +
            COLUMN_TIMESTAMP + " TEXT);";

    public LogDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }
}