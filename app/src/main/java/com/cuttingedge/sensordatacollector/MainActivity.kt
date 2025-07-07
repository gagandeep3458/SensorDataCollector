package com.cuttingedge.sensordatacollector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuttingedge.sensordatacollector.data.Sample
import com.cuttingedge.sensordatacollector.data.SensorDataBuffer
import com.cuttingedge.sensordatacollector.ui.theme.SensorDataCollectorTheme

private const val TAG = "SENSOR_STUFF"

class MainActivity : ComponentActivity(), SensorEventListener {

    lateinit var sensorManager: SensorManager
    var accelSensor: Sensor? = null

    var prevTimestamp: Long? = null

    val sensorData = SensorDataBuffer(context = this, capacity = 400)

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_FASTEST)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SensorDataCollectorTheme {
                MainScreen(sensorData = sensorData)
            }
        }

        setupSensorStuff()
    }

    private fun setupSensorStuff() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let { accelSensor ->
            Log.d(TAG, "Min Sensor Delay: ${accelSensor.minDelay}")
            Log.d(TAG, "Max Sensor Delay: ${accelSensor.maxDelay}")
        }

        sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_FASTEST)
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // TODO("Not yet implemented")
    }

    override fun onSensorChanged(p0: SensorEvent?) {
        p0?.let { e ->
            prevTimestamp?.let { pt ->
//                Log.d(TAG, "Diff in nano seconds: ${e.timestamp - pt}")
            }
            prevTimestamp = e.timestamp

            sensorData.add(
                timestamp = e.timestamp,
                x = e.values[0],
                y = e.values[1],
                z = e.values[2]
            )

            Log.d(TAG, "Data Size : ${sensorData.size}")
        }
    }
}

private fun writeCsvToUri(context: Context, uri: Uri, csvContent: String) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(csvContent.toByteArray())
            // Optional: Show a toast message
            android.widget.Toast.makeText(
                context,
                "CSV saved successfully!",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        // Optional: Show an error toast
        android.widget.Toast.makeText(
            context,
            "Error saving CSV: ${e.message}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}

// Function to generate CSV content (as shown in step 3)
fun generateCsvContent(data: List<Sample>): String {
    val header = "timestamp_nano,x,y,z"
    val stringBuilder = StringBuilder(header)
    stringBuilder.append("\n")

    data.forEach { item ->
        stringBuilder.append("${item.timestamp},${item.x},${item.y},${item.z}\n")
    }
    return stringBuilder.toString()
}

@Composable
fun MainScreen(modifier: Modifier = Modifier, sensorData: SensorDataBuffer) {

    val context = LocalContext.current

    // Use rememberLauncherForActivityResult for handling the file creation intent
    val createFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            // Write the CSV content to the selected URI
            writeCsvToUri(context, it, generateCsvContent(sensorData.exportableData))

            sensorData.clear()
        }
    }

    Box(modifier = modifier) {
        Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->

            val list = sensorData.data.collectAsState()

            LineChart(
                modifier = Modifier.padding(innerPadding),
                data = list.value,
                minVerticalAxis = sensorData.minVerticalAxis,
                maxVerticalAxis = sensorData.maxVerticalAxis
            )
        }

        val isRecording by sensorData.isRecordingFlow.collectAsState()

        Button(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 18.dp),
            onClick = {
                if (!isRecording) {
                    sensorData.startRecording()
                } else {
                    sensorData.stopRecording()
                    val defaultFileName = "xyz_data_${System.currentTimeMillis()}.csv"
                    createFileLauncher.launch(defaultFileName)
                }
            },
            colors = ButtonDefaults.buttonColors()
                .copy(containerColor = if (isRecording) Color.Red else Color.Blue)
        ) {
            Text(if (isRecording) "Stop" else "Record", color = Color.White)
        }
    }
}

@Preview
@Composable
private fun MainScreenPreview() {
    MainScreen(sensorData = SensorDataBuffer(capacity = 400))
}

@Composable
fun LineChart(
    data: List<Triple<Float, Float, Float>>, // The data points for the line chart
    minVerticalAxis: Float,
    maxVerticalAxis: Float,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface, // Background color of the chart
    showPoints: Boolean = true, // Whether to show circles at data points
    showAxes: Boolean = true // Whether to show X and Y axes
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp) // Padding around the chart
        ) {

            val width = size.width
            val height = size.height

            // Calculate min and max values for scaling
            val minValue = minVerticalAxis
            val maxValue = maxVerticalAxis

            // Adjust max value if it's equal to min value to avoid division by zero
            val range = if (maxValue == minValue) 1f else maxValue - minValue

            // Draw X and Y axes if enabled
            if (showAxes) {
                // Draw Y-axis
                drawLine(
                    color = Color.Gray,
                    start = Offset(0f, 0f),
                    end = Offset(0f, height),
                    strokeWidth = 2f
                )
                // Draw X-axis
                drawLine(
                    color = Color.Gray,
                    start = Offset(0f, height),
                    end = Offset(width, height),
                    strokeWidth = 2f
                )
            }

            // Create a path for the line
            val pathX = Path()
            val pathY = Path()
            val pathZ = Path()

            // Iterate through data points to draw the line and points
            data.forEachIndexed { index, value ->
                // Calculate x and y coordinates for each data point
                val horizontal = if (data.size > 1) {
                    index * (width / (data.size - 1))
                } else {
                    width / 2 // Center the point if only one data point
                }

                // Scale the y-value to fit within the canvas height,
                // inverting it because canvas y-axis goes from top to bottom
                val verticalX = height - ((value.first - minValue) / range) * height
                val verticalY = height - ((value.second - minValue) / range) * height
                val verticalZ = height - ((value.third - minValue) / range) * height

                // Move to the first point or draw a line to subsequent points
                if (index == 0) {
                    pathX.moveTo(horizontal, verticalX)
                    pathY.moveTo(horizontal, verticalY)
                    pathZ.moveTo(horizontal, verticalZ)
                } else {
                    pathX.lineTo(horizontal, verticalX)
                    pathY.lineTo(horizontal, verticalY)
                    pathZ.lineTo(horizontal, verticalZ)
                }

                // Draw a circle at each data point if enabled
                if (showPoints) {
                    drawCircle(
                        color = Color.Red,
                        center = Offset(horizontal, verticalX),
                        radius = 4f
                    )
                    drawCircle(
                        color = Color.Green,
                        center = Offset(horizontal, verticalY),
                        radius = 4f
                    )
                    drawCircle(
                        color = Color.Blue,
                        center = Offset(horizontal, verticalZ),
                        radius = 4f
                    )
                }
            }

            // Draw the line path
            drawPath(
                path = pathX,
                color = Color.Red,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
            drawPath(
                path = pathY,
                color = Color.Green,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
            drawPath(
                path = pathZ,
                color = Color.Blue,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }
    }
}