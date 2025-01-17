package com.specknet.pdiotapp.utils

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "ActivityHistory.db"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "ActivityHistory"
        const val COLUMN_ID = "id"
        const val COLUMN_DATE = "date"
        const val COLUMN_ACTIVITY_TYPE = "activity_type"
        const val COLUMN_DURATION = "duration"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableStatement = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_DATE TEXT,
                $COLUMN_ACTIVITY_TYPE TEXT,
                $COLUMN_DURATION INTEGER DEFAULT 0
            )
        """
        db.execSQL(createTableStatement)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun incrementActivityDurationForDate(date: String, activityType: String, durationIncrement: Int) {
        val db = writableDatabase

        // Check if an entry exists for this date and activity
        val cursor = db.rawQuery(
            "SELECT $COLUMN_DURATION FROM $TABLE_NAME WHERE $COLUMN_DATE = ? AND $COLUMN_ACTIVITY_TYPE = ?",
            arrayOf(date, activityType)
        )

        if (cursor.moveToFirst()) {
            // If the entry exists, increment the duration
            val currentDuration = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DURATION))
            val newDuration = currentDuration + durationIncrement
            val values = ContentValues().apply {
                put(COLUMN_DURATION, newDuration)
            }
            db.update(TABLE_NAME, values, "$COLUMN_DATE = ? AND $COLUMN_ACTIVITY_TYPE = ?", arrayOf(date, activityType))
        } else {
            // If not, insert a new record
            val values = ContentValues().apply {
                put(COLUMN_DATE, date)
                put(COLUMN_ACTIVITY_TYPE, activityType)
                put(COLUMN_DURATION, durationIncrement)
            }
            db.insert(TABLE_NAME, null, values)
        }
        cursor.close()
        db.close()
    }

    fun getActivitiesForDate(date: String): Map<String, Int> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT $COLUMN_ACTIVITY_TYPE, $COLUMN_DURATION FROM $TABLE_NAME WHERE $COLUMN_DATE = ?", arrayOf(date))

        val activitySummary = mutableMapOf<String, Int>()
        while (cursor.moveToNext()) {
            val activityType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACTIVITY_TYPE))
            val duration = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DURATION))
            activitySummary[activityType] = duration
        }
        cursor.close()
        db.close()
        return activitySummary
    }
}
