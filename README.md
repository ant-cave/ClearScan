# ClearScan

**A local-first Android document scanner with real-time edge guidance, multi-page workflows, PDF tools, and cloud AI translation.**

[English](README.md) | [简体中文](README.zh-CN.md)

[Download the latest release](https://github.com/ant-cave/ClearScan/releases/latest) | [Report an issue](https://github.com/ant-cave/ClearScan/issues) | [View source](https://github.com/ant-cave/ClearScan)

ClearScan is a native Android scanner built with Kotlin and Jetpack Compose. It keeps scans and document processing on the device, provides automatic and manual perspective correction, and combines everyday PDF utilities with optional cloud AI translation through any OpenAI-compatible API.

> Current release: **v1.1.0**. Public APKs target ARM64 devices running Android 8.0 or newer.

This is the ant-cave edition of ClearScan, maintained independently at [ant-cave/ClearScan](https://github.com/ant-cave/ClearScan). It is based on the original project by SuiYueMengHen, with OpenCV-accelerated filters, cloud translation, and a fully automatic release pipeline.

## Highlights

| Area | Capabilities |
| --- | --- |
| Capture | CameraX preview, real-time document boundary guidance, flash and lens controls, single-page and multi-page sessions |
| Alignment | OpenCV edge detection, confidence-based fallback, four-corner manual adjustment, high-resolution perspective correction |
| Editing | Rotation, brightness, contrast, saturation, document enhancement, high-quality cached filter previews |
| Filters | Auto, Clean, White Paper, B&W, Ink, Magic Color, Photo, Gray, Soft Gray, and High Contrast — B&W/Ink use OpenCV adaptive thresholding, sharpen uses an OpenCV unsharp mask, and white balance uses OpenCV statistics, all dramatically faster than per-pixel loops |
| Documents | Local library, search, nested folders, rename, move, delete, share, print, and password protection |
| PDF tools | Image to PDF, PDF to image, merge, split, compress, page-level editing, watermark, and signature overlays |
| Codes | Bundled ML Kit QR and barcode recognition, safe URL opening, copy, and web search actions |
| Translation | Cloud AI translation through any OpenAI-compatible chat API (DeepSeek, OpenAI, Kimi, Qwen, OpenRouter, Ollama, ...) — configure base URL, API key, and model in-app; long texts are split and translated in chunks with progress |
| Application | Follows system language (English/简体中文) and system light/dark theme by default, manual overrides available, in-app update checks against this repository, TXT and DOCX log export |

## Scan Pipeline

1. Camera frames are analyzed at a controlled rate on a dedicated worker. Old frames are discarded to keep the preview responsive.
2. A lightweight detector draws the live document guide without blocking capture.
3. After capture, ClearScan runs a higher-resolution OpenCV detector on the orientation-corrected image.
4. The detected quadrilateral remains fully adjustable before perspective correction.
5. The corrected page can be enhanced, filtered, reordered, and exported as an image or a multi-page PDF.

If a device cannot bind CameraX preview, capture, and analysis simultaneously, ClearScan falls back to preview and capture instead of terminating the camera workflow.

## Cloud Translation

Translation runs on the cloud engine of your choice. Any OpenAI-compatible `/chat/completions` endpoint works:

- DeepSeek (`https://api.deepseek.com`, `deepseek-chat`) — the default
- OpenAI, Kimi (Moonshot), Qwen (DashScope), OpenRouter, local Ollama instances, and more

Enter the base URL, API key, and model name on the Translate screen and start translating. Your key is stored only on the device and is sent only to the endpoint you configure. Text is split into chunks automatically for very long inputs.

The previous local Hy-MT2 / llama.cpp inference engine has been removed in this edition, which shrinks the APK by roughly 300 MB and removes all native compilation from the build.

## Privacy

- Documents, page images, settings, and logs are stored locally.
- ClearScan does not require a cloud account.
- Scanned documents are not uploaded to any server.
- Files leave the application only after an explicit share, export, link-opening, or update action.
- Cloud translation, if used, sends only the text you submit to the API you configure.
- Application logs record operational metadata and errors, not copies of scanned page content.

## Compatibility

| Requirement | Value |
| --- | --- |
| Minimum Android version | Android 8.0, API 26 |
| Target Android version | Android 16, API 36 |
| Public release ABI | `arm64-v8a` |
| Build JDK | JDK 17 |
| Android SDK | SDK 36 |

Camera capabilities can vary by manufacturer, so physical-device verification is recommended before production deployment.

## Install

Download the signed APK and checksum from the [releases page](https://github.com/ant-cave/ClearScan/releases/latest):

- `ClearScan-vX.Y.Z-arm64-v8a.apk`
- `ClearScan-vX.Y.Z-arm64-v8a.apk.sha256`

Releases from this repository are signed with the ClearScan release certificate of this fork. If you are upgrading from a different build (for example the original upstream APK), export important documents, uninstall the old build, and then install this one.

## Build From Source

```bash
git clone https://github.com/ant-cave/ClearScan.git
cd ClearScan
./gradlew testDebugUnitTest :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

No Android NDK or CMake installation is required — the project contains no native sources.

## Release Signing

Release builds read signing material from environment variables:

```text
CLEARSCAN_KEYSTORE_PATH
CLEARSCAN_KEYSTORE_PASSWORD
CLEARSCAN_KEY_ALIAS
CLEARSCAN_KEY_PASSWORD
```

The signing key is never committed to the repository. Pushing a `v*` tag triggers the [release workflow](.github/workflows/release.yml), which runs unit tests, builds the signed release APK, and publishes it together with its SHA-256 checksum as a GitHub release automatically.

## Project Structure

```text
app/src/main/java/com/clearscan/
  MainActivity.kt             Compose UI and application workflows
  DocumentEdgeDetector.kt     OpenCV detection and perspective correction
  DocumentFrameAnalyzer.kt    Throttled CameraX live-frame analysis
  ClearScanDatabase.kt        Room entities, DAO, and migration
  OverlayEditors.kt           Watermark and signature editors
  BarcodeAnalyzer.kt          ML Kit QR and barcode analysis
  AppUpdater.kt               GitHub release update flow
  SettingsRepository.kt       DataStore-backed application settings
  LogExporter.kt              TXT and DOCX log export
```

## Known Limitations

- Public release APKs currently target ARM64 only.
- PDF editing is page-oriented; it is not an Acrobat-style text-layout editor.
- Real-time edge guidance may fall back to capture-only mode on constrained Camera2 implementations.
- Cloud translation quality and latency depend on the provider you configure; translation requires network connectivity.

## Contributing

Bug reports, reproducible device-specific camera logs, detection fixtures, and focused pull requests are welcome. Before opening an issue, include the ClearScan version, Android version, device model, steps to reproduce, and an exported application log when available.

## Third-Party Software

ClearScan uses CameraX, Jetpack Compose, Room, OpenCV, and ML Kit. Review all applicable third-party notices before redistributing the application.

## License

ClearScan (this edition) is released under the [GNU Affero General Public License v3.0 or later](LICENSE) (AGPL-3.0-or-later).

This project is based on [ClearScan by SuiYueMengHen](https://github.com/SuiYueMengHen/ClearScan), originally released under the MIT License. The upstream MIT notice is preserved in the LICENSE file, as required by its terms.
