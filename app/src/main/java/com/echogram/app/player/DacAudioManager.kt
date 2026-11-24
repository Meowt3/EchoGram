package com.echogram.app.player

import android.content.Context
import android.media.AudioAttributes as SystemAudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import com.echogram.app.data.model.DacDevice
import com.echogram.app.data.model.DacOutputMode
import com.echogram.app.data.model.StreamFormatInfo
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hardware USB DAC & Android 14+ Bit-Perfect mixer manager.
 * - Matches Bit-Perfect integer PCM attributes to active stream sample rate & channel count.
 * - Cleans up stale device references immediately when USB DAC is unplugged.
 */
class DacAudioManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _devices = MutableStateFlow<List<DacDevice>>(emptyList())
    val devices: StateFlow<List<DacDevice>> = _devices.asStateFlow()

    private val _routingSummary = MutableStateFlow("Системный вывод Android")
    val routingSummary: StateFlow<String> = _routingSummary.asStateFlow()

    private val _bitPerfectActive = MutableStateFlow(false)
    val bitPerfectActive: StateFlow<Boolean> = _bitPerfectActive.asStateFlow()

    private val _streamFormat = MutableStateFlow(StreamFormatInfo())
    val streamFormat: StateFlow<StreamFormatInfo> = _streamFormat.asStateFlow()

    private var lastBitPerfectDevice: AudioDeviceInfo? = null
    private var lastBitPerfectAttrs: SystemAudioAttributes? = null

    @Volatile
    var onDevicesChanged: (() -> Unit)? = null

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
            onDevicesChanged?.invoke()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val removedIds = removedDevices?.map { it.id }?.toSet().orEmpty()
            if (lastBitPerfectDevice != null && lastBitPerfectDevice!!.id in removedIds) {
                clearMixerOverride()
            }
            refreshDevices()
            onDevicesChanged?.invoke()
        }
    }

    init {
        refreshDevices()
        runCatching { audioManager.registerAudioDeviceCallback(callback, mainHandler) }
    }

    fun updateStreamFormat(info: StreamFormatInfo) {
        _streamFormat.value = info
    }

    fun refreshDevices() {
        val raw = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrDefault(emptyArray())
        _devices.value = raw
            .filter { isExternalRelevant(it.type) }
            .map { toDacDevice(it) }
            .sortedWith(
                compareByDescending<DacDevice> { it.isUsbDac }
                    .thenByDescending { it.supportsBitPerfect }
                    .thenByDescending { it.maxBitDepth }
            )
    }

    fun resolveTargetDevice(mode: DacOutputMode, preferredId: Int?): AudioDeviceInfo? {
        val raw = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrDefault(emptyArray())
        if (preferredId != null && preferredId > 0) {
            raw.firstOrNull { it.id == preferredId }?.let { return it }
        }
        if (mode == DacOutputMode.AUTO) return null
        return raw.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
            ?: raw.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
            ?: raw.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY }
            ?: raw.firstOrNull { isExternalRelevant(it.type) }
    }

    fun buildMedia3AudioAttributes(): Media3AudioAttributes =
        Media3AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

    private fun buildSystemAudioAttributes(): SystemAudioAttributes =
        SystemAudioAttributes.Builder()
            .setUsage(SystemAudioAttributes.USAGE_MEDIA)
            .setContentType(SystemAudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    fun applyMixerRouting(targetDevice: AudioDeviceInfo?, mode: DacOutputMode): Boolean {
        clearMixerOverride()
        var bpActive = false
        if (mode == DacOutputMode.BIT_PERFECT &&
            targetDevice != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            bpActive = applyBitPerfectApi34(buildSystemAudioAttributes(), targetDevice, _streamFormat.value)
        }
        _bitPerfectActive.value = bpActive
        _routingSummary.value = buildSummary(targetDevice, mode, bpActive)
        return bpActive
    }

    fun clearMixerOverride() {
        val prevDev = lastBitPerfectDevice
        val prevAttrs = lastBitPerfectAttrs
        lastBitPerfectDevice = null
        lastBitPerfectAttrs = null
        if (prevDev != null && prevAttrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { clearBitPerfectApi34(prevAttrs, prevDev) }
        }
        _bitPerfectActive.value = false
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun applyBitPerfectApi34(
        attrs: SystemAudioAttributes,
        device: AudioDeviceInfo,
        stream: StreamFormatInfo
    ): Boolean = runCatching {
        val candidates = audioManager.getSupportedMixerAttributes(device)
            .filter {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                    it.format.encoding != AudioFormat.ENCODING_PCM_FLOAT
            }
        if (candidates.isEmpty()) return@runCatching false

        val targetRate = stream.sampleRateHz.takeIf { it > 0 } ?: 44100
        val best = candidates.minWithOrNull(
            compareBy<AudioMixerAttributes> { abs(it.format.sampleRate - targetRate) }
                .thenByDescending { integerEncodingScore(it.format.encoding) }
        ) ?: return@runCatching false

        val applied = audioManager.setPreferredMixerAttributes(attrs, device, best)
        if (applied) {
            lastBitPerfectDevice = device
            lastBitPerfectAttrs = attrs
        }
        applied
    }.getOrDefault(false)

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun clearBitPerfectApi34(attrs: SystemAudioAttributes, device: AudioDeviceInfo) {
        audioManager.clearPreferredMixerAttributes(attrs, device)
    }

    private fun integerEncodingScore(enc: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enc == AudioFormat.ENCODING_PCM_32BIT) return 32
            if (enc == AudioFormat.ENCODING_PCM_24BIT_PACKED) return 24
        }
        return if (enc == AudioFormat.ENCODING_PCM_16BIT) 16 else 8
    }

    private fun buildSummary(dev: AudioDeviceInfo?, mode: DacOutputMode, bpActive: Boolean): String {
        if (mode == DacOutputMode.AUTO && dev == null) {
            val usb = _devices.value.firstOrNull { it.isUsbDac }
            return if (usb != null) "Найден USB ЦАП: ${usb.name} (Авто)" else "Системный вывод Android"
        }
        if (dev == null) return "Внешний ЦАП не подключён"
        val name = dev.productName?.toString()?.trim().orEmpty().ifBlank { typeLabel(dev.type) }
        return when {
            bpActive -> "Bit-Perfect активен: $name"
            mode == DacOutputMode.BIT_PERFECT -> "Direct (без DSP): $name"
            else -> "USB ЦАП: $name"
        }
    }

    private fun toDacDevice(dev: AudioDeviceInfo): DacDevice {
        val encs = dev.encodings
        val maxBits = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && encs.contains(AudioFormat.ENCODING_PCM_32BIT) -> 32
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && encs.contains(AudioFormat.ENCODING_PCM_24BIT_PACKED) -> 24
            else -> 16
        }
        val bp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                audioManager.getSupportedMixerAttributes(dev)
                    .any { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
            }.getOrDefault(false)
        } else false
        val label = typeLabel(dev.type)
        val name = dev.productName?.toString()?.trim().orEmpty().ifBlank { label }
        return DacDevice(
            id = dev.id,
            name = name,
            typeLabel = label,
            isUsbDac = dev.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_USB_ACCESSORY,
            maxBitDepth = maxBits,
            sampleRatesHz = dev.sampleRates.filter { it > 0 }.distinct().sorted(),
            supportsBitPerfect = bp
        )
    }

    private fun isExternalRelevant(type: Int): Boolean = type in setOf(
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET
    )

    private fun typeLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB ЦАП"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-C ЦАП"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Audio"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Цифровой выход"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Проводные наушники"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Проводная гарнитура"
        else -> "Аудиовыход"
    }

    companion object {
        @Volatile
        private var instance: DacAudioManager? = null

        fun getInstance(context: Context): DacAudioManager =
            instance ?: synchronized(this) {
                instance ?: DacAudioManager(context.applicationContext).also { instance = it }
            }
    }
}
