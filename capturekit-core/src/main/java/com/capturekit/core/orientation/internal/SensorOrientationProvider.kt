package com.capturekit.core.orientation.internal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.capturekit.core.orientation.DeviceOrientation
import com.capturekit.core.orientation.OrientationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Internal provider that wraps the Android SensorManager to emit orientation changes.
 */
internal class SensorOrientationProvider(
    context: Context,
    private val levelTolerance: Float,
    private val samplingRateUs: Int = SensorManager.SENSOR_DELAY_UI
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _orientationFlow = MutableStateFlow(
        DeviceOrientation(OrientationType.PORTRAIT, 0, 0f, 0f, 0f, true, System.currentTimeMillis())
    )
    
    /**
     * Flow of continuous orientation updates.
     */
    val orientationFlow: StateFlow<DeviceOrientation> = _orientationFlow.asStateFlow()
    
    /**
     * The current orientation.
     */
    val currentOrientation: DeviceOrientation get() = _orientationFlow.value
    
    /**
     * The current quaternion.
     */
    var currentQuaternion = FloatArray(4)
        private set

    private var isStarted = false

    /**
     * Starts listening for sensor updates.
     */
    fun start() {
        if (isStarted) return
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, samplingRateUs)
            isStarted = true
        }
    }

    /**
     * Stops listening for sensor updates.
     */
    fun stop() {
        if (!isStarted) return
        sensorManager?.unregisterListener(this)
        isStarted = false
    }

    /**
     * Cleans up resources.
     */
    fun release() {
        stop()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR || 
            event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            
            SensorManager.getQuaternionFromVector(currentQuaternion, event.values)
            
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            
            val orientationValues = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientationValues)
            
            // Convert to degrees
            val yaw = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
            val pitch = Math.toDegrees(orientationValues[1].toDouble()).toFloat()
            val roll = Math.toDegrees(orientationValues[2].toDouble()).toFloat()
            
            // Determine orientation type
            var degrees = 0
            if (pitch < -45 && pitch > -135) {
                // Device is flat
            } else {
                val rot = Math.toDegrees(atan2(-rotationMatrix[1].toDouble(), rotationMatrix[4].toDouble())).toFloat()
                degrees = ((rot + 360) % 360).roundToInt()
            }
            
            val type = OrientationType.fromDegrees(degrees)
            val isLevel = abs(pitch) < levelTolerance && abs(roll) < levelTolerance
            
            _orientationFlow.value = DeviceOrientation(
                type = type,
                rotationDegrees = degrees,
                pitch = pitch,
                roll = roll,
                yaw = yaw,
                isLevel = isLevel,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
