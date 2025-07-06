package com.cuttingedge.sensordatacollector.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SensorDataBuffer(val capacity: Int) {

    private val x_buffer = FloatArray(capacity)
    private val y_buffer = FloatArray(capacity)
    private val z_buffer = FloatArray(capacity)

    private var writeIndex = 0

    private var currentSize = 0

    private val lock = ReentrantLock()

    private val _dataFlow = MutableStateFlow<List<Float>>(emptyList())
    val data: StateFlow<List<Float>> = _dataFlow.asStateFlow()

    val size: Int get() {
        return lock.withLock {
            currentSize
        }
    }

    fun add(x: Float, y: Float, z: Float) {
        lock.withLock {
            x_buffer[writeIndex] = x
            y_buffer[writeIndex] = y
            z_buffer[writeIndex] = z
            writeIndex = (writeIndex + 1) % capacity // Wrap around
            if (currentSize < capacity) {
                currentSize++ // Increment size until buffer is full
            }
        }
        _dataFlow.value = getCurrentBufferAsList()
    }

    fun getAll(): List<Float> {
        lock.withLock {
            return _dataFlow.value
        }
    }

    private fun getCurrentBufferAsList(): List<Float> {
        if (currentSize == 0) {
            return emptyList()
        }

        val result = mutableListOf<Float>()
        if (currentSize < capacity) {
            // If buffer is not yet full, values are from 0 to currentSize - 1
            for (i in 0 until currentSize) {
                result.add(z_buffer[i])
            }
        } else {
            // If buffer is full, values wrap around.
            // Read from oldest (at writeIndex) to newest (just before writeIndex).
            for (i in 0 until capacity) {
                val readIndex = (writeIndex + i) % capacity
                result.add(z_buffer[readIndex])
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
        }
    }
}