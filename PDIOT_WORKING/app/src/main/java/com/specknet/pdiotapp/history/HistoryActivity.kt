package com.specknet.pdiotapp.history

import android.os.Bundle
import android.widget.CalendarView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.DatabaseHelper
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var historyTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // Initialize the database helper and views
        dbHelper = DatabaseHelper(this)
        historyTextView = findViewById(R.id.historyTextView)
        val calendarView = findViewById<CalendarView>(R.id.calendarView)

        // Set a listener for date selection
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            // Format the selected date as "yyyy-MM-dd"
            val selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            displayHistoryForDate(selectedDate)
        }
    }

    private fun displayHistoryForDate(date: String) {
        // Retrieve activity summary for the selected date
        val activitySummary = dbHelper.getActivitiesForDate(date)

        // Format and display the activity summary
        historyTextView.text = if (activitySummary.isNotEmpty()) {
            activitySummary.entries.joinToString("\n\n") { (activity, duration) ->
                val hours = duration / 3600
                val minutes = (duration % 3600) / 60
                val seconds = duration % 60
                "$activity: ${hours}h ${minutes}m ${seconds}s"
            }
        } else {
            "No activities recorded on this date"
        }
    }
}
