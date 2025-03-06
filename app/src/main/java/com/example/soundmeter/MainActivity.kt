package com.example.soundmeter
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var soundLevel by mutableFloatStateOf(0f)
    private var peakSoundLevel by mutableFloatStateOf(0f)
    private val SOUND_THRESHOLD = 70f

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSoundMeasurement()
        } else {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SoundUI(
                currentLevel = soundLevel,
                peakLevel = peakSoundLevel,
                threshold = SOUND_THRESHOLD,
                onResetPeak = { peakSoundLevel = 0f }
            )
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startSoundMeasurement()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startSoundMeasurement() {
        if (isRecording) return

        val scope = lifecycleScope
        scope.launch(Dispatchers.IO) {
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE
                )

                val buffer = ShortArray(BUFFER_SIZE)
                audioRecord?.startRecording()
                isRecording = true

                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, BUFFER_SIZE) ?: 0
                    if (read > 0) {
                        var sum = 0.0
                        for (sample in buffer) {
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / buffer.size).coerceAtLeast(1.0) // Prevent -Infinity
                        val db = 20 * log10(rms / 32768.0) + 90
                        soundLevel = db.toFloat()
                        peakSoundLevel = max(peakSoundLevel, soundLevel)
                    }
                    Thread.sleep(100)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopSoundMeasurement() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSoundMeasurement()
    }
}

@Composable
fun SoundUI(
    currentLevel: Float,
    peakLevel: Float,
    threshold: Float,
    onResetPeak: () -> Unit
) {
    val soundColor = when {
        currentLevel > threshold -> Color.Red
        currentLevel > 60 -> Color.Yellow
        currentLevel > 40 -> Color.Green
        else -> Color.Blue
    }

    val maxValue = 100f
    val progress = (currentLevel / maxValue).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Sound Meter",
            fontSize = 28.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Current Sound Level", fontSize = 18.sp, color = Color.Gray)
                Text("${String.format("%.1f", currentLevel)} dB", fontSize = 36.sp, color = soundColor)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .padding(vertical = 8.dp)
                        .background(Color.LightGray)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(soundColor)
                    )
                }
                if (currentLevel > threshold) {
                    Text("WARNING: HIGH NOISE LEVEL!", color = Color.Red, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Peak Sound Level", fontSize = 18.sp, color = Color.Gray)
                Text("${String.format("%.1f", peakLevel)} dB", fontSize = 28.sp)
                Button(
                    onClick = onResetPeak,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Reset Peak")
                }
            }
        }
    }
}
