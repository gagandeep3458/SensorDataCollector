package com.cuttingedge.sensordatacollector.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class Sample(val timestamp: Long, val x: Float, val y: Float, val z: Float)

class SensorDataBuffer(val context: Context? = null, val capacity: Int) {

    private val xBuffer = FloatArray(capacity)
    private val yBuffer = FloatArray(capacity)
    private val zBuffer = FloatArray(capacity)

    val exportableData = mutableListOf<Sample>()

    var minVerticalAxis = 0f
    var maxVerticalAxis = 0f

    private var writeIndex = 0

    private var currentSize = 0

    private val lock = ReentrantLock()

    private val _isRecordingFlow = MutableStateFlow(false)
    val isRecordingFlow: StateFlow<Boolean> = _isRecordingFlow.asStateFlow()

    private val _dataFlow = MutableStateFlow<List<Triple<Float, Float, Float>>>(emptyList())
    val data: StateFlow<List<Triple<Float, Float, Float>>> = _dataFlow.asStateFlow()

    val size: Int
        get() {
            return lock.withLock {
                currentSize
            }
        }

    fun add(timestamp: Long, x: Float, y: Float, z: Float) {
        lock.withLock {
            xBuffer[writeIndex] = x
            yBuffer[writeIndex] = y
            zBuffer[writeIndex] = z
            writeIndex = (writeIndex + 1) % capacity // Wrap around
            if (currentSize < capacity) {
                currentSize++ // Increment size until buffer is full
            }

            // If recording add sample to exported list
            if (isRecordingFlow.value) {
                exportableData.add(Sample(timestamp, x, y, z))
            }

            // Assign Vertical axis scaling

            val sample = listOf(x, y, z)

            val possibleMin = sample.min()
            val possibleMax = sample.max()

            if (possibleMin < minVerticalAxis) {
                minVerticalAxis = possibleMin
            }

            if (possibleMax > maxVerticalAxis) {
                maxVerticalAxis = possibleMax
            }
        }
        _dataFlow.value = getCurrentBufferAsList()
    }

    fun getAll(): List<Triple<Float, Float, Float>> {
        lock.withLock {
            return _dataFlow.value
        }
    }

    private fun getCurrentBufferAsList(): List<Triple<Float, Float, Float>> {
        if (currentSize == 0) {
            return emptyList()
        }

        val result = mutableListOf<Triple<Float, Float, Float>>()
        if (currentSize < capacity) {
            // If buffer is not yet full, values are from 0 to currentSize - 1
            for (i in 0 until currentSize) {
                result.add(Triple(xBuffer[i], yBuffer[i], zBuffer[i]))
            }
        } else {
            // If buffer is full, values wrap around.
            // Read from oldest (at writeIndex) to newest (just before writeIndex).
            for (i in 0 until capacity) {
                val readIndex = (writeIndex + i) % capacity
                result.add(Triple(xBuffer[readIndex], yBuffer[readIndex], zBuffer[readIndex]))
            }
        }
        return result
    }

    fun clear() {
        lock.withLock {
            writeIndex = 0
            currentSize = 0
            // Optionally, fill with default values if needed, but not strictly necessary for correctness
            // buffer.fill(0f)
            _dataFlow.value = emptyList() // Clear the observable flow as well
            exportableData.clear()
        }
    }

    fun startRecording() {
        if (exportableData.isNotEmpty()) {
            exportableData.clear()
        }
        _isRecordingFlow.value = true
    }

    fun stopRecording() {
        _isRecordingFlow.value = false
    }

}