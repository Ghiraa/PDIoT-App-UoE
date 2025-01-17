package com.specknet.pdiotapp.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.ThingyLiveData
import com.specknet.pdiotapp.utils.DatabaseHelper
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class LiveDataActivity : AppCompatActivity() {

    // UI components and database helper
    lateinit var respeckChart: LineChart
    lateinit var thingyChart: LineChart
    lateinit var activityResultTextView: TextView
    lateinit var respiratoryResultTextView: TextView
    private lateinit var dbHelper: DatabaseHelper

    // Model interpreters and buffers
    private lateinit var activityClassifier: Interpreter
    private lateinit var respiratoryClassifier: Interpreter
    private val activityWindowBuffer = mutableListOf<FloatArray>()
    private val respiratoryWindowBuffer = mutableListOf<FloatArray>()

    // Broadcast receivers and filters
    private lateinit var respeckLiveUpdateReceiver: BroadcastReceiver
    private lateinit var thingyLiveUpdateReceiver: BroadcastReceiver
    lateinit var looperRespeck: Looper
    lateinit var looperThingy: Looper

    // Constants
    private val activityClasses = mapOf(
        0 to "Ascending stairs", 1 to "Descending stairs", 2 to "Lying down on back",
        3 to "Lying down on left side", 4 to "Lying down on right side", 5 to "Lying down on stomach",
        6 to "Miscellaneous", 7 to "Normal walking", 8 to "Running",
        9 to "Shuffle walking", 10 to "Sitting / Standing"
    )

    private val activityEmojis = mapOf(
        0 to "⬆️",  // Ascending stairs
        1 to "⬇️",  // Descending stairs
        2 to "🛌",  // Lying down on back
        3 to "🛌",  // Lying down on left side
        4 to "🛌",  // Lying down on right side
        5 to "🛌",  // Lying down on stomach
        6 to "🤹",  // Miscellaneous movements
        7 to "🚶",  // Normal walking
        8 to "🏃",  // Running
        9 to "👣",  // Shuffle walking
        10 to "🪑"  // Sitting/Standing
    )

    private val respiratoryClasses = mapOf(
        0 to "Coughing", 1 to "Hyperventilating", 2 to "Breathing normally", 3 to "Other"
    )

    private val socialEmojis = mapOf(
        0 to "🤧",             // Coughing
        1 to "😤",     // Hyperventilating
        2 to "😌",   // Breathing normally
        3 to "🗣️🍴🎤😂", // Other,
        4 to "❌"
    )

    private val stationaryClasses = setOf(2, 3, 4, 5, 10)
    private val windowSize = 50
    private var latestRespeckData: FloatArray? = null
    private var latestThingyData: FloatArray? = null
    var time = 0f

    // Chart data sets
    lateinit var dataSet_res_accel_x: LineDataSet
    lateinit var dataSet_res_accel_y: LineDataSet
    lateinit var dataSet_res_accel_z: LineDataSet
    lateinit var dataSet_thingy_accel_x: LineDataSet
    lateinit var dataSet_thingy_accel_y: LineDataSet
    lateinit var dataSet_thingy_accel_z: LineDataSet
    lateinit var allRespeckData: LineData
    lateinit var allThingyData: LineData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_data)

        // Initialize UI components
        activityResultTextView = findViewById(R.id.classificationTextView)
        respiratoryResultTextView = findViewById(R.id.respiratoryClassificationTextView)
        respeckChart = findViewById(R.id.respeck_chart)
        thingyChart = findViewById(R.id.thingy_chart)
        dbHelper = DatabaseHelper(this)

        setupCharts()
        activityClassifier = Interpreter(loadModelFile("activity_model.tflite"))
        respiratoryClassifier = Interpreter(loadModelFile("social_model.tflite"))

        // Register broadcast receivers
        respeckLiveUpdateReceiver = createBroadcastReceiver("respeck")
        registerReceiver(respeckLiveUpdateReceiver, IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST))

        thingyLiveUpdateReceiver = createBroadcastReceiver("thingy")
        registerReceiver(thingyLiveUpdateReceiver, IntentFilter(Constants.ACTION_THINGY_BROADCAST))
    }

    private fun createBroadcastReceiver(type: String): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val liveData = when (type) {
                    "respeck" -> intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    "thingy" -> intent.getSerializableExtra(Constants.THINGY_LIVE_DATA) as ThingyLiveData
                    else -> return
                }
                updateSensorData(type, liveData)
            }
        }
    }

    private fun updateSensorData(type: String, liveData: Any) {
        val data = when (liveData) {
            is RESpeckLiveData -> floatArrayOf(liveData.accelX, liveData.accelY, liveData.accelZ)
            is ThingyLiveData -> floatArrayOf(liveData.accelX, liveData.accelY, liveData.accelZ)
            else -> return
        }

        if (type == "respeck") {
            latestRespeckData = data
            updateGraph(type, data[0], data[1], data[2])
        } else if (type == "thingy") {
            latestThingyData = data
            updateGraph(type, data[0], data[1], data[2])
        }

        updateWindow()
    }

    private fun updateWindow() {
        if (latestRespeckData != null && latestThingyData != null) {
            val combinedData = latestRespeckData!! + latestThingyData!!
            activityWindowBuffer.add(combinedData)
            respiratoryWindowBuffer.add(latestRespeckData!!)

            if (activityWindowBuffer.size == windowSize) classifyActivity()
            latestRespeckData = null
            latestThingyData = null
        }
    }

    private fun classifyActivity() {
        val activityInput = arrayOf(activityWindowBuffer.toTypedArray())
        val activityOutput = Array(1) { FloatArray(activityClasses.size) }
        activityClassifier.run(activityInput, activityOutput)

        val predictedActivityIndex = activityOutput[0].indices.maxByOrNull { activityOutput[0][it] } ?: -1
        val predictedActivity = activityClasses[predictedActivityIndex] ?: "Unknown"

        // If the activity is stationary, classify respiratory activity
        var predictedRespiratory = "Not Applicable"
        var predictedRespiratoryIndex = 0
        if (predictedActivityIndex in stationaryClasses) {
            val respiratoryInput = arrayOf(respiratoryWindowBuffer.toTypedArray())
            val respiratoryOutput = Array(1) { FloatArray(respiratoryClasses.size) }
            respiratoryClassifier.run(respiratoryInput, respiratoryOutput)

            predictedRespiratoryIndex = respiratoryOutput[0].indices.maxByOrNull { respiratoryOutput[0][it] } ?: 4
            predictedRespiratory = respiratoryClasses[predictedRespiratoryIndex] ?: "Unknown"


        }
        if (predictedRespiratory == "Not Applicable") {
            predictedRespiratoryIndex = 4
        }

        runOnUiThread {
            activityResultTextView.text = "Activity Performed: \n" + activityEmojis[predictedActivityIndex] + predictedActivity
            respiratoryResultTextView.text = "Social Signal: \n" + socialEmojis[predictedRespiratoryIndex] + predictedRespiratory
        }

        // Log only activity prediction in the database
        logActivityPrediction(predictedActivity)
        activityWindowBuffer.clear()
        respiratoryWindowBuffer.clear()
    }

    private fun logActivityPrediction(activityType: String) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        dbHelper.incrementActivityDurationForDate(date, activityType, 2)
    }

    fun setupCharts() {
        respeckChart = findViewById(R.id.respeck_chart)
        thingyChart = findViewById(R.id.thingy_chart)

        // Initialize Respeck chart with X, Y, and Z data series
        val entries_res_accel_x = ArrayList<Entry>()
        val entries_res_accel_y = ArrayList<Entry>()
        val entries_res_accel_z = ArrayList<Entry>()

        dataSet_res_accel_x = LineDataSet(entries_res_accel_x, "Accel X")
        dataSet_res_accel_y = LineDataSet(entries_res_accel_y, "Accel Y")
        dataSet_res_accel_z = LineDataSet(entries_res_accel_z, "Accel Z")

        dataSet_res_accel_x.setDrawCircles(false)
        dataSet_res_accel_y.setDrawCircles(false)
        dataSet_res_accel_z.setDrawCircles(false)

        dataSet_res_accel_x.color = ContextCompat.getColor(this, R.color.red)
        dataSet_res_accel_y.color = ContextCompat.getColor(this, R.color.green)
        dataSet_res_accel_z.color = ContextCompat.getColor(this, R.color.blue)

        val dataSetsRes = ArrayList<ILineDataSet>()
        dataSetsRes.add(dataSet_res_accel_x)
        dataSetsRes.add(dataSet_res_accel_y)
        dataSetsRes.add(dataSet_res_accel_z)

        allRespeckData = LineData(dataSetsRes)
        respeckChart.data = allRespeckData
        respeckChart.invalidate()

        // Initialize Thingy chart with X, Y, and Z data series
        val entries_thingy_accel_x = ArrayList<Entry>()
        val entries_thingy_accel_y = ArrayList<Entry>()
        val entries_thingy_accel_z = ArrayList<Entry>()

        dataSet_thingy_accel_x = LineDataSet(entries_thingy_accel_x, "Accel X")
        dataSet_thingy_accel_y = LineDataSet(entries_thingy_accel_y, "Accel Y")
        dataSet_thingy_accel_z = LineDataSet(entries_thingy_accel_z, "Accel Z")

        dataSet_thingy_accel_x.setDrawCircles(false)
        dataSet_thingy_accel_y.setDrawCircles(false)
        dataSet_thingy_accel_z.setDrawCircles(false)

        dataSet_thingy_accel_x.color = ContextCompat.getColor(this, R.color.red)
        dataSet_thingy_accel_y.color = ContextCompat.getColor(this, R.color.green)
        dataSet_thingy_accel_z.color = ContextCompat.getColor(this, R.color.blue)

        val dataSetsThingy = ArrayList<ILineDataSet>()
        dataSetsThingy.add(dataSet_thingy_accel_x)
        dataSetsThingy.add(dataSet_thingy_accel_y)
        dataSetsThingy.add(dataSet_thingy_accel_z)

        allThingyData = LineData(dataSetsThingy)
        thingyChart.data = allThingyData
        thingyChart.invalidate()
    }

    fun updateGraph(graph: String, x: Float, y: Float, z: Float) {
        // Update the specified graph with new data points for X, Y, and Z
        if (graph == "respeck") {
            dataSet_res_accel_x.addEntry(Entry(time, x))
            dataSet_res_accel_y.addEntry(Entry(time, y))
            dataSet_res_accel_z.addEntry(Entry(time, z))

            runOnUiThread {
                allRespeckData.notifyDataChanged()
                respeckChart.notifyDataSetChanged()
                respeckChart.invalidate()
                respeckChart.setVisibleXRangeMaximum(150f)
                respeckChart.moveViewToX(respeckChart.lowestVisibleX + 40)
            }
        } else if (graph == "thingy") {
            dataSet_thingy_accel_x.addEntry(Entry(time, x))
            dataSet_thingy_accel_y.addEntry(Entry(time, y))
            dataSet_thingy_accel_z.addEntry(Entry(time, z))

            runOnUiThread {
                allThingyData.notifyDataChanged()
                thingyChart.notifyDataSetChanged()
                thingyChart.invalidate()
                thingyChart.setVisibleXRangeMaximum(150f)
                thingyChart.moveViewToX(thingyChart.lowestVisibleX + 40)
            }
        }
        time += 1
    }

    @Throws(IOException::class)
    private fun loadModelFile(model: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(model)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckLiveUpdateReceiver)
        unregisterReceiver(thingyLiveUpdateReceiver)
        activityClassifier.close()
        respiratoryClassifier.close()
    }
}
