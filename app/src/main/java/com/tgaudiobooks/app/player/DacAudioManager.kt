package com.tgaudiobooks.app.player

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
import com.tgaudiobooks.app.data.model.AudioStreamFormatInfo
import com.tgaudiobooks.app.data.model.DacDeviceInfo
import com.tgaudiobooks.app.data.model.DacOutputMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hardware audio output / USB DAC manager.
 * - Live-detects USB DACs, USB-C dongles, digital line-outs, and wired outputs via AudioDeviceCallback.
 * - Extracts real supported sample rates, PCM bit depth, and Android 14+ Bit-Perfect mixer capabilities.
 * - Configures ExoPlayer preferred audio routing, AudioAttributes, and Bit-Perfect mixer bypass.
 */
class DacAudioManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _devices = MutableStateFlow<List<DacDeviceInfo>>(emptyList())
    val devices: StateFlow<List<DacDeviceInfo>> = _devices.asStateFlow()

    private val _activeRoutingSummary = MutableStateFlow("Системный вывод")
    val activeRoutingSummary: StateFlow<String> = _activeRoutingSummary.asStateFlow()

    private val _isBitPerfectMixerActive = MutableStateFlow(false)
    val isBitPerfectMixerActive: StateFlow<Boolean> = _isBitPerfectMixerActive.asStateFlow()

    private val _streamFormat = MutableStateFlow(AudioStreamFormatInfo())
    val streamFormat: StateFlow<AudioStreamFormatInfo> = _streamFormat.asStateFlow()

    fun updateStreamFormat(info: AudioStreamFormatInfo) {
        _streamFormat.value = info
    }

    private var lastConfiguredBitPerfectDevice: AudioDeviceInfo? = null
    private var lastConfiguredSystemAttributes: SystemAudioAttributes? = null

    @Volatile
    var onDevicesChangedListener: (() -> Unit)? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
            onDevicesChangedListener?.invoke()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val removedIds = removedDevices?.map { it.id }?.toSet().orEmpty()
            if (lastConfiguredBitPerfectDevice != null && lastConfiguredBitPerfectDevice!!.id in removedIds) {
                clearMixerOverride()
            }
            refreshDevices()
            onDevicesChangedListener?.invoke()
        }
    }

    init {
        refreshDevices()
        runCatching {
            audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        }
    }

    fun refreshDevices() {
        val rawOutputs = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrDefault(emptyArray())

        val mapped = rawOutputs
            .filter { isExternalOrDacRelevant(it.type) }
            .map { toDacDeviceInfo(it) }
            .sortedWith(
                compareByDescending<DacDeviceInfo> { it.isUsbDac }
                    .thenByDescending { it.supportsBitPerfectMixer }
                    .thenByDescending { it.maxBitDepth }
                    .thenBy { it.name.lowercase() }
            )
        _devices.value = mapped
    }

    fun resolveTargetAudioDevice(mode: DacOutputMode, preferredDeviceId: Int?): AudioDeviceInfo? {
        val rawOutputs = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrDefault(emptyArray())

        if (preferredDeviceId != null && preferredDeviceId > 0) {
            val exact = rawOutputs.firstOrNull { it.id == preferredDeviceId }
            if (exact != null) return exact
        }

        if (mode == DacOutputMode.AUTO) {
            return null
        }

        // Priority: USB DAC -> USB Headset/Dongle -> Digital Line -> Wired Headphones
        return rawOutputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
            ?: rawOutputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
            ?: rawOutputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY }
            ?: rawOutputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_LINE_DIGITAL }
            ?: rawOutputs.firstOrNull { isExternalOrDacRelevant(it.type) }
    }

    fun buildMedia3AudioAttributes(mode: DacOutputMode): Media3AudioAttributes {
        return Media3AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }

    fun buildSystemAudioAttributes(mode: DacOutputMode): SystemAudioAttributes {
        return SystemAudioAttributes.Builder()
            .setUsage(SystemAudioAttributes.USAGE_MEDIA)
            .setContentType(SystemAudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    /**
     * Applies Android 14+ Bit-Perfect USB mixer bypass when requested and supported,
     * and updates the human-readable routing status.
     */
    fun applyMixerRouting(targetDevice: AudioDeviceInfo?, mode: DacOutputMode): Boolean {
        clearPreviousMixerOverride()

        val sysAttrs = buildSystemAudioAttributes(mode)
        var bitPerfectApplied = false

        if (mode == DacOutputMode.BIT_PERFECT &&
            targetDevice != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            bitPerfectApplied = applyBitPerfectMixerApi34(sysAttrs, targetDevice)
        }

        _isBitPerfectMixerActive.value = bitPerfectApplied
        _activeRoutingSummary.value = buildSummary(targetDevice, mode, bitPerfectApplied)
        return bitPerfectApplied
    }

    fun clearMixerOverride() = clearPreviousMixerOverride()

    private fun clearPreviousMixerOverride() {
        val prevDev = lastConfiguredBitPerfectDevice
        val prevAttrs = lastConfiguredSystemAttributes
        lastConfiguredBitPerfectDevice = null
        lastConfiguredSystemAttributes = null
        if (prevDev != null &&
            prevAttrs != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            clearBitPerfectMixerApi34(prevAttrs, prevDev)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun applyBitPerfectMixerApi34(
        attrs: SystemAudioAttributes,
        device: AudioDeviceInfo
    ): Boolean {
        return runCatching {
            val supported = audioManager.getSupportedMixerAttributes(device)
            val bitPerfectCandidates = supported.filter {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                    it.format.encoding != AudioFormat.ENCODING_PCM_FLOAT
            }
            if (bitPerfectCandidates.isEmpty()) return@runCatching false

            val targetRate = _streamFormat.value.sampleRateHz.takeIf { it > 0 } ?: 44100
            val best = bitPerfectCandidates.minWithOrNull(
                compareBy<AudioMixerAttributes> { kotlin.math.abs(it.format.sampleRate - targetRate) }
                    .thenByDescending { encodingBitScore(it.format.encoding) }
            ) ?: return@runCatching false

            val applied = audioManager.setPreferredMixerAttributes(attrs, device, best)
            if (applied) {
                lastConfiguredBitPerfectDevice = device
                lastConfiguredSystemAttributes = attrs
            }
            applied
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun clearBitPerfectMixerApi34(
        attrs: SystemAudioAttributes,
        device: AudioDeviceInfo
    ) {
        runCatching {
            audioManager.clearPreferredMixerAttributes(attrs, device)
        }
    }

    private fun buildSummary(
        targetDevice: AudioDeviceInfo?,
        mode: DacOutputMode,
        bitPerfectApplied: Boolean
    ): String {
        if (mode == DacOutputMode.AUTO && targetDevice == null) {
            val usb = _devices.value.firstOrNull { it.isUsbDac }
            return if (usb != null) {
                "Обнаружен USB ЦАП: ${usb.name} (режим Авто)"
            } else {
                "Системный вывод Android"
            }
        }
        if (targetDevice == null) {
            return "Внешний ЦАП не подключён — активен встроенный вывод"
        }
        val devName = targetDevice.productName?.toString()?.trim().orEmpty()
            .ifBlank { typeLabelFor(targetDevice.type) }
        return when {
            bitPerfectApplied -> "Bit-Perfect активен: $devName"
            mode == DacOutputMode.BIT_PERFECT -> "Direct (без DSP): $devName"
            else -> "Hi-Res USB ЦАП: $devName"
        }
    }

    private fun toDacDeviceInfo(device: AudioDeviceInfo): DacDeviceInfo {
        val encodings = device.encodings
        val supportsFloat = encodings.contains(AudioFormat.ENCODING_PCM_FLOAT)
        val has32Bit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            encodings.contains(AudioFormat.ENCODING_PCM_32BIT)
        } else {
            false
        }
        val has24Bit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            encodings.contains(AudioFormat.ENCODING_PCM_24BIT_PACKED)
        } else {
            false
        }
        val maxBits = when {
            supportsFloat || has32Bit -> 32
            has24Bit -> 24
            else -> 16
        }
        val rates = device.sampleRates
            .filter { it > 0 }
            .distinct()
            .sorted()

        val bitPerfectSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                audioManager.getSupportedMixerAttributes(device)
                    .any { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
            }.getOrDefault(false)
        } else {
            false
        }

        val typeLbl = typeLabelFor(device.type)
        val rawName = device.productName?.toString()?.trim().orEmpty()
        val displayName = rawName.ifBlank { typeLbl }

        return DacDeviceInfo(
            id = device.id,
            name = displayName,
            typeLabel = typeLbl,
            isUsbDac = isUsbDeviceType(device.type),
            sampleRatesHz = rates,
            maxBitDepth = maxBits,
            supportsFloatPcm = supportsFloat,
            supportsBitPerfectMixer = bitPerfectSupported
        )
    }

    private fun encodingBitScore(encoding: Int): Int {
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) return 40
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (encoding == AudioFormat.ENCODING_PCM_32BIT) return 32
            if (encoding == AudioFormat.ENCODING_PCM_24BIT_PACKED) return 24
        }
        if (encoding == AudioFormat.ENCODING_PCM_16BIT) return 16
        return 8
    }

    private fun isUsbDeviceType(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> true
        else -> false
    }

    private fun isExternalOrDacRelevant(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_AUX_LINE,
        AudioDeviceInfo.TYPE_HDMI -> true
        else -> false
    }

    private fun typeLabelFor(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB ЦАП"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-C ЦАП / Гарнитура"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Audio"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Цифровой выход (SPDIF/Coax)"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Проводные наушники (3.5 мм)"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Проводная гарнитура"
        AudioDeviceInfo.TYPE_AUX_LINE -> "Линейный выход (AUX)"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI Audio"
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
