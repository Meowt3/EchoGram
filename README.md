<div align="center">

<img src="assets/icon.svg" alt="EchoGram" width="96" />

# EchoGram

**Open-source native Android audiobook player & Material 3 streaming Telegram client powered by TDLib and Media3.**

[![English](https://img.shields.io/badge/Language-English-2ea44f?style=for-the-badge)](README.md) [![Русский](https://img.shields.io/badge/Язык-Русский-1f6feb?style=for-the-badge)](README_RU.md)

[![Latest Release](https://img.shields.io/github/v/release/Meowt3/EchoGram?style=flat-square&logo=github&color=2ea44f)](https://github.com/Meowt3/EchoGram/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Meowt3/EchoGram/total?style=flat-square&logo=github)](https://github.com/Meowt3/EchoGram/releases)
[![Build APK](https://img.shields.io/github/actions/workflow/status/Meowt3/EchoGram/build.yml?style=flat-square&label=build)](https://github.com/Meowt3/EchoGram/actions/workflows/build.yml)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20%7C%20API%2035-3DDC84?style=flat-square&logo=android&logoColor=white)](https://github.com/Meowt3/EchoGram/releases/latest)
[![TDLib](https://img.shields.io/badge/TDLib-1.8.56-26A5E4?style=flat-square&logo=telegram&logoColor=white)](https://github.com/tdlib/td)
[![Media3](https://img.shields.io/badge/Media3-1.4.1-blue?style=flat-square)](https://github.com/androidx/media)
[![License](https://img.shields.io/github/license/Meowt3/EchoGram?style=flat-square)](LICENSE)

[Download](#download) · [Features](#features) · [Audio & USB DAC](#audio--usb-dac) · [Architecture](#architecture) · [Build from Source](#build-from-source)

</div>

> **Русский:** Открытый нативный Android-плеер аудиокниг и стриминговый Telegram-клиент в стиле **Material 3** на базе **TDLib** и **Media3 (ExoPlayer)**. Прогрессивный MTProto-стриминг без ожидания загрузки, офлайн-кеш, динамические темы под обложку, миксы и серии, DSP-эквалайзер, **Bit-Perfect USB DAC**, вход по номеру или QR-коду и облачная синхронизация через «Избранное» Telegram. Подробное описание на русском — в [README_RU.md](README_RU.md).

---

## Overview

**EchoGram** turns your Telegram channels, groups, bots, and Saved Messages into a dedicated, ad-free audiobook streaming app for Android. Built natively in **Kotlin** and **Jetpack Compose**, it pairs a **Material 3** interface with **TDLib** MTProto networking and a custom **AndroidX Media3 (ExoPlayer)** progressive streaming engine.

## Download

Get the latest signed Release APK from [GitHub Releases](https://github.com/Meowt3/EchoGram/releases/latest):

| Architecture | Package | Best For |
| --- | --- | --- |
| **`arm64-v8a`** *(Recommended)* | [`EchoGram-v2.1.4-arm64-v8a-release.apk`](https://github.com/Meowt3/EchoGram/releases/latest/download/EchoGram-v2.1.4-arm64-v8a-release.apk) | Modern 64-bit Android smartphones (smallest size) |
| **`armeabi-v7a`** | [`EchoGram-v2.1.4-armeabi-v7a-release.apk`](https://github.com/Meowt3/EchoGram/releases/latest/download/EchoGram-v2.1.4-armeabi-v7a-release.apk) | Older 32-bit Android phones & Android TV boxes |
| **`x86_64`** | [`EchoGram-v2.1.4-x86_64-release.apk`](https://github.com/Meowt3/EchoGram/releases/latest/download/EchoGram-v2.1.4-x86_64-release.apk) | Emulators, Chromebooks, WSA & x86_64 tablets |
| **Universal** | [`EchoGram-v2.1.4-universal-release.apk`](https://github.com/Meowt3/EchoGram/releases/latest/download/EchoGram-v2.1.4-universal-release.apk) | Any Android 8.0+ device (if unsure) |

### Quick Start

1. Download and install the latest APK from [Releases](https://github.com/Meowt3/EchoGram/releases/latest).
2. Sign in to Telegram using your **phone number** or **QR code** (one-tap login via an installed Telegram app is supported). If needed, configure an **MTProto / SOCKS5 / HTTP proxy** directly on the sign-in screen.
3. Choose the Telegram channels, bots, or chats you want to use as audiobook sources in **Settings → Sources** — your Home feed, categories, series, and search populate automatically.

## Features

- **Material 3 Streaming Interface**: Dynamic color extraction from audiobook cover art (`Palette`), Light / Dark / System themes, 7 live progress bar animations (*Wave, Glow, Segments, Equalizer, Comet, Dots, Classic*), 5 selectable launcher icons, and a home-screen mini-player widget.
- **Smart Discovery & Library**: Personalized Home mixes and auto-playlists, automatic category discovery (by hashtags, metadata, themes, and duration), multi-part book series grouping, favorites, custom playlists, listening statistics, and full-text search across your library.
- **Native Telegram Integration (TDLib)**: Full MTProto client supporting main & archived chats, built-in Telegram chat viewer with messaging and bot commands, pre-login proxy management (`tg://proxy`, `t.me/proxy`, `tg://socks`), and zero-setup cloud sync of progress, playlists, and bookmarks via Telegram **Saved Messages** (plus local JSON backup).
- **Progressive Streaming & Offline Mode**: Custom Media3 `BaseDataSource` streams audio chunks directly from Telegram with instant seek and automatic stall recovery. Download any audiobook for offline playback with full cache controls.
- **Audiobook-First Controls**: Exact per-track resume positions, smart rewind after pauses, variable playback speed, **Skip Silence**, configurable skip intervals (`10s / 15s / 30s`), Sleep Timer (minutes or end of track), timestamped bookmarks with notes, and embedded ID3 chapter navigation.

## Audio & USB DAC

EchoGram is engineered for clean speech intelligibility and Hi-Res external audio hardware:

| Capability | Details |
| --- | --- |
| **Supported Formats** | `MP3`, `M4A`, `M4B`, `AAC`, `OGG`, `OPUS`, `FLAC`, `WAV`, `AMR`, `MKA`, `WEBM` |
| **DSP & Voice Clarity** | Hardware Equalizer presets (*Standard, Clear Voice, Night Mode, Deep Bass*), Loudness Boost (`+0` to `+12 dB`), and Skip Silence |
| **USB DAC & Bit-Perfect** | Live USB-C DAC detection (`AudioDeviceCallback`), direct device routing, and Android 14+ integer PCM **Bit-Perfect** mixer bypass matching the active stream sample rate |
| **Background Playback** | Media3 `MediaSessionService` with lock-screen & notification controls (`Prev / -15s / Play-Pause / +15s / Next`), headset button support, and network wake lock |

## Architecture

| Layer | Technology |
| --- | --- |
| **Language & UI** | Kotlin `2.0.21`, Jetpack Compose, Material 3, AndroidX Palette, Coil |
| **Telegram Core** | [TDLib](https://github.com/tdlib/td) `1.8.56` via [tdlibx/td](https://github.com/tdlibx/td) (JNI MTProto) |
| **Audio Engine** | [AndroidX Media3](https://github.com/androidx/media) `1.4.1` (`ExoPlayer`, `MediaSessionService`, custom `TdlibAudioDataSource`) |
| **QR Sign-In** | [ZXing Core](https://github.com/zxing/zxing) `3.5.3` (`tg://login` QR & deep-link) |
| **Target Platform** | Android 8.0–15+ (`minSdk 26`, `targetSdk 35`) |

## Build from Source

1. Install **JDK 17** and the **Android SDK (API 35)**.
2. Obtain your Telegram API credentials (`api_id` and `api_hash`) from [my.telegram.org](https://my.telegram.org).
3. Create `local.properties` in the project root (or export `TG_API_ID` / `TG_API_HASH` environment variables):
   ```properties
   tg.api.id=YOUR_API_ID
   tg.api.hash=YOUR_API_HASH
   ```
4. Build the APK:
   ```bash
   gradle assembleRelease
   ```
   The compiled APK will be generated at `app/build/outputs/apk/release/app-release.apk`.

## Credits & License

- [TDLib](https://github.com/tdlib/td) & [tdlibx/td](https://github.com/tdlibx/td) — Telegram Database Library
- [AndroidX Media3](https://github.com/androidx/media) — Media playback and session architecture
- [ZXing](https://github.com/zxing/zxing) — QR code encoding

Licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE) for details.
