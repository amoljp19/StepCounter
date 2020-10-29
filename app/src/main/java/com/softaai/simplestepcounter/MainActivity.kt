package com.softaai.simplestepcounter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import java.text.DateFormat
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.absoluteValue


const val TAG = "SimpleStepCounter"

enum class FitActionRequestCode {
    SUBSCRIBE,
    READ_DATA,
    PAST_14_DAYS_DATA
}


class MainActivity : AppCompatActivity() {

    val PAST_14_DAYS = 14

    private val fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_CUMULATIVE)
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
            .build()

    private val runningQOrLater =
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q

    private lateinit var dataSets: ArrayList<BarDataSet>

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissionsAndRun(FitActionRequestCode.SUBSCRIBE)
        //displayLastTwoWeeksData()
    }

    override fun onResume() {
        super.onResume()

    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkPermissionsAndRun(fitActionRequestCode: FitActionRequestCode) {
        if (permissionApproved()) {
            fitSignIn(fitActionRequestCode)
        } else {
            requestRuntimePermissions(fitActionRequestCode)
        }
    }

    private fun fitSignIn(requestCode: FitActionRequestCode) {
        if (oAuthPermissionsApproved()) {
            performActionForRequestCode(requestCode)
        } else {
            requestCode.let {
                GoogleSignIn.requestPermissions(
                        this,
                        requestCode.ordinal,
                        getGoogleAccount(), fitnessOptions
                )
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (resultCode) {
            RESULT_OK -> {
                val postSignInAction = FitActionRequestCode.values()[requestCode]
                postSignInAction.let {
                    performActionForRequestCode(postSignInAction)
                }
            }
            else -> oAuthErrorMsg(requestCode, resultCode)
        }
    }

    private fun oAuthPermissionsApproved() = GoogleSignIn.hasPermissions(
            getGoogleAccount(),
            fitnessOptions
    )

    private fun performActionForRequestCode(requestCode: FitActionRequestCode) = when (requestCode) {
        FitActionRequestCode.READ_DATA -> readData()
        FitActionRequestCode.SUBSCRIBE -> subscribe()
        FitActionRequestCode.PAST_14_DAYS_DATA -> displayLastTwoWeeksData()
    }

    private fun oAuthErrorMsg(requestCode: Int, resultCode: Int) {
        val message = """
            There was an error signing into Fit. Check the troubleshooting section of the README
            for potential issues.
            Request code was: $requestCode
            Result code was: $resultCode
        """.trimIndent()
        Log.e(TAG, message)
    }

    private fun subscribe() {
        Fitness.getRecordingClient(this, getGoogleAccount())
                .subscribe(DataType.TYPE_STEP_COUNT_CUMULATIVE)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i(TAG, "Successfully subscribed!")
                    } else {
                        Log.w(TAG, "There was a problem subscribing.", task.exception)
                    }
                }
    }

    private fun readData(){
        Fitness.getHistoryClient(this, getGoogleAccount())
                .readDailyTotal(DataType.TYPE_STEP_COUNT_DELTA)
                .addOnSuccessListener { dataSet ->
                    val total = when {
                        dataSet.isEmpty -> 0
                        else -> dataSet.dataPoints.first().getValue(Field.FIELD_STEPS).asInt()
                    }
                    Log.i(TAG, "Total steps: $total")
                    tv_steps.text = "Updated steps : " + total
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "There was a problem getting the step count.", e)
                }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_read_data) {
            fitSignIn(FitActionRequestCode.READ_DATA)
            return true
        }
        if (id == R.id.action_past_14_days_data) {
            fitSignIn(FitActionRequestCode.PAST_14_DAYS_DATA)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun getGoogleAccount() = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun permissionApproved(): Boolean {
        val approved = if (runningQOrLater) {
            PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION
            )
        } else {
            true
        }
        return approved
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestRuntimePermissions(requestCode: FitActionRequestCode) {
        val shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.ACTIVITY_RECOGNITION
                )

        requestCode.let {
            if (shouldProvideRationale) {
                Log.i(TAG, "Displaying permission rationale to provide additional context.")
                Snackbar.make(
                        findViewById(R.id.main_activity_view),
                        R.string.permission_rationale,
                        Snackbar.LENGTH_INDEFINITE
                )
                        .setAction(R.string.ok) {
                            ActivityCompat.requestPermissions(
                                    this,
                                    arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                                    requestCode.ordinal
                            )
                        }
                        .show()
            } else {
                Log.i(TAG, "Requesting permission")
                ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                        requestCode.ordinal
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>,
            grantResults: IntArray
    ) {
        when {
            grantResults.isEmpty() -> {
                Log.i(TAG, "User interaction was cancelled.")
            }
            grantResults[0] == PackageManager.PERMISSION_GRANTED -> {
                val fitActionRequestCode = FitActionRequestCode.values()[requestCode]
                fitActionRequestCode.let {
                    fitSignIn(fitActionRequestCode)
                }
            }
            else -> {
                Snackbar.make(
                        findViewById(R.id.main_activity_view),
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE
                )
                        .setAction(R.string.settings) {
                            val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            val uri = Uri.fromParts(
                                    "package",
                                    BuildConfig.APPLICATION_ID, null
                            )
                            intent.data = uri
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }
                        .show()
            }
        }
    }

    private fun displayLastTwoWeeksData(){

        val cal: Calendar = Calendar.getInstance()
        val now = Date()
        cal.setTime(now)
        val endTime: Long = cal.timeInMillis
        cal.add(Calendar.DATE, -14)
        val startTime: Long = cal.timeInMillis

        val dateFormat: DateFormat = DateFormat.getDateInstance()
        val timeFormat = DateFormat.getTimeInstance()
        Log.e("History", "Range Start: " + dateFormat.format(startTime))
        Log.e("History", "Range End: " + dateFormat.format(endTime))

        val readRequest = DataReadRequest.Builder()
            .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
            .bucketByTime(1, TimeUnit.DAYS)
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .build()

            Fitness.getHistoryClient(this, getGoogleAccount())
                .readData(readRequest)
                .addOnSuccessListener {
                    it.buckets

                    if (it.buckets.size > 0) {
                        Log.e("History", "Number of buckets: " + it.buckets.size)
                        for (bucket in it.buckets) {
                            val dataSets: List<DataSet> = bucket.getDataSets()
                            for (dataSet in dataSets) {
                                showDataSet(dataSet, dateFormat, timeFormat)
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "There was a problem getting the step count.", e)
                }


    }

    
    private fun showDataSet(dataSet: DataSet, dateFormat: DateFormat, timeFormat: DateFormat){
        var barDataSet:BarDataSet? = null
        var dataSets = arrayListOf<BarDataSet>()
        for (dp in dataSet.getDataPoints()) {
            var valueSet = arrayListOf<BarEntry>();
            Log.e("History", "Data point:")
            Log.e("History", "\tType: " + dp.getDataType().getName())
            Log.e(
                    "History",
                    "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS))
                            .toString() + " " + timeFormat.format(
                            dp.getStartTime(
                                    TimeUnit.MILLISECONDS
                            )
                    )
            )
            Log.e(
                    "History",
                    "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS))
                            .toString() + " " + timeFormat.format(
                            dp.getStartTime(
                                    TimeUnit.MILLISECONDS
                            )
                    )
            )

            var day =dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)).toString().split("-")[0];
            Log.e(
                    "History",
                    "\tday: " + day
            )
            for (field in dp.getDataType().getFields()) {
                Log.e(
                        "History", "\tField: " + field.name +
                        " Value: " + dp.getValue(field)
                )

                val v = BarEntry(day.toFloat(), dp.getValue(field).asInt().toFloat())
                valueSet.add(v)
            }
            barDataSet = BarDataSet(valueSet, dataSet.dataType.name)
        }

        val chart: BarChart = findViewById<View>(R.id.chart) as BarChart

        val data = BarData()
        barDataSet.let {
            data.addDataSet(barDataSet)
            chart.setData(data)
            val description = Description()
            description.text = "Last 14 days Steps Chart"
            chart.description = description
            chart.animateXY(2000, 2000)
            chart.invalidate()
        }


    }

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private fun getXAxisValues(): ArrayList<Any> {
        val cal: Calendar = Calendar.getInstance()
        val now = Date()
        cal.setTime(now)
        val xAxis = ArrayList<Any>()
        for(i:Int in 14 downTo 1){
            cal.add(Calendar.DATE, -i)
            val day:String = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH)
            xAxis.add(day)
        }
        return xAxis
    }

}