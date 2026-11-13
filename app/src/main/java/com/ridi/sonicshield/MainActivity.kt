package com.ridi.sonicshield

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

private enum class SoundMode(val label: String, val subtitle: String) {
    ANC("Noise Monitor", "Menganalisis kebisingan; bukan pembatalan ANC akustik."),
    TRANSPARENCY("Transparency", "Suara sekitar difilter dan diperkuat ke TWS."),
    OFF("Off", "Pemantauan dan pemrosesan mic dihentikan.")
}

class MainActivity : ComponentActivity() {
    private var engine: LiveAudioEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SonicShieldScreen(this, ::startAudio, ::stopAudio) }
    }

    private fun startAudio(mode: SoundMode, onStatus: (String) -> Unit) {
        stopAudio()
        engine = LiveAudioEngine(this, mode) { status -> runOnUiThread { onStatus(status) } }.also { it.start() }
    }

    private fun stopAudio() {
        engine?.stop()
        engine = null
    }

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }
}

@Composable
private fun SonicShieldScreen(
    activity: ComponentActivity,
    startAudio: (SoundMode, (String) -> Unit) -> Unit,
    stopAudio: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(SoundMode.OFF) }
    var deviceStatus by remember { mutableStateOf("Belum diperiksa") }
    var deviceName by remember { mutableStateOf("Belum terdeteksi") }
    var micStatus by remember { mutableStateOf("Belum diuji") }
    var processingStatus by remember { mutableStateOf("Tidak aktif") }
    var ancDetectionStatus by remember { mutableStateOf("Belum diperiksa") }
    var ancDetectionDetail by remember { mutableStateOf("Tekan deteksi untuk memeriksa profil audio dan jalur Bluetooth yang tersedia.") }
    var permissionGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val micOk = grants[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val btOk = Build.VERSION.SDK_INT < 31 || grants[Manifest.permission.BLUETOOTH_CONNECT] == true || ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        permissionGranted = micOk && btOk
        if (permissionGranted) {
            val result = inspectBluetooth(activity)
            deviceStatus = result.first
            deviceName = result.second
            micStatus = "Mic HP siap. Mic TWS tidak dipakai agar Bluetooth tidak masuk mode panggilan."
        } else {
            deviceStatus = if (!btOk) "Izin Bluetooth tidak diberikan" else "Bluetooth siap"
            micStatus = if (!micOk) "Izin mikrofon belum diberikan" else "Mic siap"
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF82C4FF), secondary = Color(0xFF7DE0BD), background = Color(0xFF080D16), surface = Color(0xFF121C2B))) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF14243B), Color(0xFF080D16), Color(0xFF080D16))))
        ) {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, null, tint = Color(0xFF82C4FF), modifier = Modifier.size(27.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("SONICSHIELD", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                }
                Text("POWERED BY RIDI  •  SMART AUDIO", color = Color(0xFF91A5BF), fontSize = 10.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(22.dp))

                val isRunning = selectedMode != SoundMode.OFF && processingStatus.startsWith("Aktif")
                val orbScale by animateFloatAsState(if (isRunning) 1.04f else 1f, label = "orb")
                val orbColor by animateColorAsState(if (isRunning) Color(0xFF2D8BCE) else Color(0xFF20334C), label = "orbColor")
                Box(Modifier.size(142.dp).scale(orbScale).border(1.dp, Color(0xFF527BA7), CircleShape).background(orbColor.copy(alpha = 0.24f), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(112.dp).border(1.dp, Color(0x667FC4FF), CircleShape).background(Color(0x332C78B8), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Headphones, null, tint = Color(0xFFA9D7FF), modifier = Modifier.size(53.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(if (isRunning) "AUDIO ENGINE BERJALAN" else "AUDIO ENGINE SIAGA", color = if (isRunning) Color(0xFF7DE0BD) else Color(0xFF9EB4CE), fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))

                Text("Pilih mode", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Pilih mode audio. Output musik tetap melalui Bluetooth normal.", color = Color(0xFF9BAEC4), fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(13.dp))

                SoundMode.values().forEach { mode ->
                    val active = selectedMode == mode
                    val accent = when (mode) { SoundMode.ANC -> Color(0xFF7DBBFF); SoundMode.TRANSPARENCY -> Color(0xFF7DE0BD); SoundMode.OFF -> Color(0xFFB7C3D3) }
                    Card(
                        onClick = {
                            selectedMode = mode
                            if (mode == SoundMode.OFF) {
                                stopAudio()
                                processingStatus = "Tidak aktif"
                            } else if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && (Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)) {
                                startAudio(mode) { processingStatus = it }
                                processingStatus = "Menyiapkan mic HP dan output A2DP..."
                            } else {
                                selectedMode = SoundMode.OFF
                                permissionLauncher.launch(buildList {
                                    add(Manifest.permission.RECORD_AUDIO)
                                    if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
                                }.toTypedArray())
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).border(if (active) 1.5.dp else 0.7.dp, if (active) accent else Color(0x334F6580), RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = if (active) accent.copy(alpha = 0.16f) else Color(0x99121D2C)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(when (mode) { SoundMode.ANC -> Icons.Default.GraphicEq; SoundMode.TRANSPARENCY -> Icons.Default.Mic; SoundMode.OFF -> Icons.Default.PowerSettingsNew }, null, tint = accent, modifier = Modifier.size(25.dp))
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(mode.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(mode.subtitle, color = Color(0xFFA2B2C5), fontSize = 10.sp, lineHeight = 14.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(25.dp).border(1.5.dp, if (active) accent else Color(0xFF66758A), CircleShape).background(if (active) accent.copy(alpha = 0.22f) else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                                if (active) Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(13.dp))
                Button(
                    onClick = {
                        val missing = buildList {
                            if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.BLUETOOTH_CONNECT)
                            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
                        }
                        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
                        else {
                            permissionGranted = true
                            val result = inspectBluetooth(activity)
                            deviceStatus = result.first
                            deviceName = result.second
                            micStatus = "Input mic HP; output A2DP diperiksa saat mode dijalankan."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245F96)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Bluetooth, null)
                    Spacer(Modifier.width(8.dp))
                    Text("PERIKSA KONEKSI TWS", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xAA111B29)), shape = RoundedCornerShape(17.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("HARDWARE ANC DETECTION", color = Color(0xFF8DBEFF), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp)
                        Text(ancDetectionStatus, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(ancDetectionDetail, color = Color(0xFFA2B2C5), fontSize = 11.sp, lineHeight = 16.sp)
                        Button(
                            onClick = {
                                val report = inspectHardwareAnc(activity)
                                ancDetectionStatus = report.title
                                ancDetectionDetail = report.detail
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245F96)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.GraphicEq, null)
                            Spacer(Modifier.width(8.dp))
                            Text("DETEKSI HARDWARE ANC", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xAA111B29)), shape = RoundedCornerShape(17.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("STATUS SISTEM", color = Color(0xFF8DBEFF), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp)
                        InfoLine("Bluetooth", deviceStatus)
                        InfoLine("Perangkat", deviceName)
                        InfoLine("Mikrofon", micStatus)
                        InfoLine("Pemrosesan", processingStatus)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("CATATAN: Software Reduce hanya menganalisis level kebisingan; tidak membatalkan suara luar secara akustik. Transparency meneruskan audio mic yang sudah difilter dan diperkuat. Keduanya bukan ANC hardware.", color = Color(0xFFFFD28A), fontSize = 10.sp, lineHeight = 15.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Text("SonicShield 3.0.14 • Powered by Ridi", color = Color(0xFF6F8198), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = Color(0xFF91A2B7), fontSize = 11.sp)
        Spacer(Modifier.width(10.dp))
        Text(value, color = Color(0xFFE4ECF6), fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

private class LiveAudioEngine(
    private val context: Context,
    private val mode: SoundMode,
    private val onStatus: (String) -> Unit
) {
    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var player: AudioTrack? = null
    private val sampleRate = 48000

    fun start() {
        if (!running.compareAndSet(false, true)) return
        worker = Thread({ processLoop() }, "SonicShield-Audio").apply { start() }
    }

    private fun processLoop() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val oldMode = audioManager.mode
        try {
            // Keep normal media routing. Never request SCO/HFP or communication mode.
            audioManager.mode = AudioManager.MODE_NORMAL

            val a2dpOutput = if (mode == SoundMode.TRANSPARENCY) {
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
                    ?: throw IllegalStateException("Output Bluetooth A2DP tidak ditemukan. Hubungkan TWS dari pengaturan Bluetooth.")
            } else null

            val minIn = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minIn <= 0) throw IllegalStateException("Format input 48 kHz tidak didukung perangkat.")
            val inputBufferBytes = maxOf(minIn, 1920) * 2
            val inputFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            recorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(inputFormat)
                .setBufferSizeInBytes(inputBufferBytes)
                .build()

            // Only Transparency creates an output track. Software Reduce is strictly
            // microphone analysis: no mic samples are sent to the TWS or phone speaker.
            if (mode == SoundMode.TRANSPARENCY) {
                val minOut = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minOut <= 0) throw IllegalStateException("Format output 48 kHz tidak didukung perangkat.")
                val outputFormat = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
                player = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(outputFormat)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(maxOf(minOut, 1920) * 2)
                    .build()
                player?.preferredDevice = a2dpOutput
            }

            recorder?.startRecording()
            if (mode == SoundMode.TRANSPARENCY) player?.play()
            Thread.sleep(250)

            val inputName = recorder?.routedDevice?.productName?.toString() ?: "Mikrofon HP"
            if (mode == SoundMode.TRANSPARENCY) {
                val actualOutput = player?.routedDevice
                if (actualOutput == null || actualOutput.type != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    throw IllegalStateException("Android belum mengarahkan audio ke A2DP TWS. Audio dihentikan agar tidak keluar dari speaker HP.")
                }
                onStatus("Aktif • Transparency • input $inputName • output A2DP • filter + AGC")
            } else {
                onStatus("Aktif • Noise Monitor • mic hanya analisis • tidak ada audio mic ke TWS • bukan ANC")
            }

            val buffer = ShortArray(maxOf(480, inputBufferBytes / 4))
            // Transparency filter state: remove handling noise/DC, soften harsh highs,
            // automatic gain control, and a peak limiter to avoid sudden loud output.
            var hpPreviousInput = 0f
            var hpPreviousOutput = 0f
            var lpPrevious = 0f
            var currentGain = 1.0f
            var lastMeterUpdate = System.currentTimeMillis()

            while (running.get()) {
                val read = recorder?.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) ?: -1
                if (read > 0) {
                    var sumSquares = 0.0
                    var peak = 0
                    for (i in 0 until read) {
                        val sample = buffer[i].toInt()
                        val magnitude = kotlin.math.abs(sample)
                        if (magnitude > peak) peak = magnitude
                        sumSquares += sample.toDouble() * sample.toDouble()
                    }
                    val rms = kotlin.math.sqrt(sumSquares / read).toFloat()

                    if (mode == SoundMode.ANC) {
                        // Meter only. Deliberately do not create/write an AudioTrack here.
                        val now = System.currentTimeMillis()
                        if (now - lastMeterUpdate >= 900L) {
                            val db = if (rms > 0.5f) (20.0 * kotlin.math.log10(rms / 32768.0)).toInt() else -90
                            onStatus("Aktif • Noise Monitor • level sekitar ${db} dBFS • tidak ada audio mic ke TWS • ANC akustik tidak aktif")
                            lastMeterUpdate = now
                        }
                    } else {
                        // Transparency: high-pass around 100 Hz, gentle low-pass around 8 kHz,
                        // smooth AGC (target RMS), and a limiter. This is a monitor, not ANC.
                        val targetRms = 5200f
                        val desiredGain = if (rms > 80f) (targetRms / rms).coerceIn(0.65f, 3.2f) else 1.0f
                        currentGain += (desiredGain - currentGain) * if (desiredGain < currentGain) 0.16f else 0.045f
                        for (i in 0 until read) {
                            val x = buffer[i].toFloat()
                            val highPassed = x - hpPreviousInput + 0.986f * hpPreviousOutput
                            hpPreviousInput = x
                            hpPreviousOutput = highPassed
                            lpPrevious += 0.50f * (highPassed - lpPrevious)
                            val amplified = lpPrevious * currentGain
                            buffer[i] = amplified.coerceIn(-28000f, 28000f).toInt().toShort()
                        }
                        player?.write(buffer, 0, read, AudioTrack.WRITE_BLOCKING)
                        val now = System.currentTimeMillis()
                        if (now - lastMeterUpdate >= 1200L) {
                            onStatus("Aktif • Transparency • mic difilter + AGC • output A2DP")
                            lastMeterUpdate = now
                        }
                    }
                } else if (read < 0) {
                    throw IllegalStateException("Pembacaan mic HP gagal: $read")
                }
            }
        } catch (e: SecurityException) {
            if (running.get()) onStatus("Izin mikrofon/Bluetooth diperlukan.")
        } catch (e: Exception) {
            if (running.get()) onStatus("Audio berhenti: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            releaseAudio()
            audioManager.mode = oldMode
            running.set(false)
        }
    }

    private fun releaseAudio() {
        try { recorder?.stop() } catch (_: Exception) { }
        try { player?.pause(); player?.flush(); player?.stop() } catch (_: Exception) { }
        try { recorder?.release() } catch (_: Exception) { }
        try { player?.release() } catch (_: Exception) { }
        recorder = null
        player = null
    }

    fun stop() {
        running.set(false)
        try { recorder?.stop() } catch (_: Exception) { }
        try { player?.pause() } catch (_: Exception) { }
        worker?.join(700)
        worker = null
    }
}

private fun inspectBluetooth(context: Context): Pair<String, String> {
    return try {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: return "Bluetooth tidak tersedia" to "-"
        if (!adapter.isEnabled) return "Bluetooth mati" to "-"
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val allOutputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val a2dpOutputs = allOutputs.filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        val bleOutputs = if (Build.VERSION.SDK_INT >= 33) {
            allOutputs.filter { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
        } else emptyList()
        val leAudioPhoneSupport = if (Build.VERSION.SDK_INT >= 33) {
            try {
                adapter.isLeAudioSupported == BluetoothStatusCodes.FEATURE_SUPPORTED
            } catch (_: Exception) { false }
        } else false
        val names = (a2dpOutputs + bleOutputs).map { it.productName?.toString() ?: "TWS Bluetooth" }.distinct()
        val status = when {
            bleOutputs.isNotEmpty() -> "Output LE Audio terdeteksi • dukungan input mic perlu diuji"
            a2dpOutputs.isNotEmpty() && leAudioPhoneSupport -> "TWS terhubung sebagai A2DP • HP mendukung LE Audio, tetapi TWS belum terdeteksi sebagai LE Audio"
            a2dpOutputs.isNotEmpty() -> "TWS A2DP terdeteksi • SBC/AAC bukan bukti dukungan mic LE Audio"
            else -> "TWS belum muncul sebagai output audio Bluetooth"
        }
        status to names.joinToString(", ").ifBlank { "Tidak ditemukan" }
    } catch (_: SecurityException) {
        "Izin Bluetooth diperlukan" to "-"
    } catch (e: Exception) {
        "Pemeriksaan gagal: ${e.javaClass.simpleName}" to "-"
    }
}


private data class AncDetectionReport(val title: String, val detail: String)

/**
 * Android exposes Bluetooth audio transports and profiles, but has no public,
 * vendor-neutral API that reports or toggles a TWS earbud's acoustic ANC state.
 * Therefore this routine reports transport evidence only and never guesses ANC
 * from the product name, codec, or charging-case display.
 */
private fun inspectHardwareAnc(context: Context): AncDetectionReport {
    return try {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
            ?: return AncDetectionReport("Bluetooth tidak tersedia", "Tidak ada adapter Bluetooth pada perangkat ini.")
        if (!adapter.isEnabled) {
            return AncDetectionReport("Bluetooth mati", "Nyalakan Bluetooth dan hubungkan TWS, lalu jalankan deteksi lagi.")
        }

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val a2dp = outputs.filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        val ble = if (Build.VERSION.SDK_INT >= 33) {
            outputs.filter { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
        } else emptyList()
        val leAudioPhone = if (Build.VERSION.SDK_INT >= 33) {
            try { adapter.isLeAudioSupported == BluetoothStatusCodes.FEATURE_SUPPORTED }
            catch (_: Exception) { false }
        } else false
        val connectedLeAudioProfile = if (Build.VERSION.SDK_INT >= 33) {
            try { adapter.getProfileConnectionState(BluetoothProfile.LE_AUDIO) == BluetoothProfile.STATE_CONNECTED }
            catch (_: Exception) { false }
        } else false
        val names = (ble + a2dp).mapNotNull { it.productName?.toString() }.distinct()
        val deviceLabel = names.joinToString().ifBlank { "nama perangkat tidak tersedia" }

        when {
            ble.isNotEmpty() || connectedLeAudioProfile -> AncDetectionReport(
                "LE Audio terdeteksi • ANC belum terverifikasi",
                "Jalur LE Audio ditemukan untuk $deviceLabel. Ini hanya membuktikan transport audio LE; Android tetap tidak menyediakan API umum untuk memastikan ANC hardware atau mengubah level ANC. Periksa kontrol bawaan TWS untuk konfirmasi."
            )
            a2dp.isNotEmpty() -> AncDetectionReport(
                "TWS terdeteksi via Bluetooth A2DP",
                "$deviceLabel tersambung melalui A2DP. Android tidak mengekspos status atau kontrol ANC universal pada jalur ini. SBC/AAC dan nama perangkat tidak dapat membuktikan ada atau tidaknya ANC. ANC bawaan mungkin tetap bekerja melalui tombol/casing TWS, tetapi belum bisa dikendalikan SonicShield."
            )
            leAudioPhone -> AncDetectionReport(
                "HP mendukung LE Audio • TWS belum terdeteksi",
                "Ponsel melaporkan dukungan LE Audio, tetapi output TWS belum muncul sebagai LE Audio atau A2DP. Hubungkan TWS lalu ulangi pemeriksaan. Dukungan LE Audio sendiri bukan bukti ANC."
            )
            else -> AncDetectionReport(
                "TWS belum terdeteksi sebagai output audio",
                "Hubungkan TWS ke ponsel terlebih dahulu. Setelah terhubung, jalankan deteksi lagi."
            )
        }
    } catch (_: SecurityException) {
        AncDetectionReport("Izin Bluetooth diperlukan", "Izinkan akses perangkat Bluetooth, lalu ulangi pemeriksaan.")
    } catch (e: Exception) {
        AncDetectionReport("Deteksi gagal", "Pemeriksaan Bluetooth gagal: ${e.javaClass.simpleName}.")
    }
}
