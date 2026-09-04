# ClearScan

**一款本地优先的 Android 文档扫描应用：实时边缘引导、多页工作流、PDF 工具与云端 AI 翻译。**

[English](README.md) | [简体中文](README.zh-CN.md)

[下载最新版本](https://github.com/ant-cave/ClearScan/releases/latest) | [反馈问题](https://github.com/ant-cave/ClearScan/issues) | [查看源码](https://github.com/ant-cave/ClearScan)

ClearScan 是使用 Kotlin 与 Jetpack Compose 构建的原生 Android 扫描应用。扫描与文档处理全部在本机完成，提供自动与手动透视校正，并整合了常用 PDF 工具和可选的云端 AI 翻译（支持任意 OpenAI 兼容接口）。

> 当前版本：**v1.1.0**。公开发布的 APK 面向 Android 8.0 及以上版本的 ARM64 设备。

本仓库是 ClearScan 的 ant-cave 版本，在 [ant-cave/ClearScan](https://github.com/ant-cave/ClearScan) 独立维护。项目基于 SuiYueMengHen 的原版 ClearScan，新增了 OpenCV 加速滤镜、云端翻译以及全自动发布流水线。

## 主要功能

| 类别 | 能力 |
| --- | --- |
| 拍摄 | CameraX 预览、实时文档边界引导、闪光灯与镜头控制、单页和多页扫描 |
| 校正 | OpenCV 边缘检测、置信度回退、四角手动调节、高分辨率透视校正 |
| 编辑 | 旋转、亮度、对比度、饱和度、文档增强、高质量缓存滤镜预览 |
| 滤镜 | Auto、Clean、White Paper、B&W、Ink、Magic Color、Photo、Gray、Soft Gray、High Contrast —— B&W/Ink 采用 OpenCV 自适应阈值，锐化采用 OpenCV unsharp mask，白平衡采用 OpenCV 统计计算，速度远快于逐像素处理 |
| 文档 | 本地文档库、搜索、多级文件夹、重命名、移动、删除、分享、打印、密码保护 |
| PDF 工具 | 图片转 PDF、PDF 转图片、合并、拆分、压缩、页面级编辑、水印、签名叠加 |
| 二维码 | 内置 ML Kit 二维码/条形码识别、安全打开链接、复制、网页搜索 |
| 翻译 | 通过任意 OpenAI 兼容对话接口（DeepSeek、OpenAI、Kimi、通义、OpenRouter、Ollama 等）进行云端 AI 翻译 —— 在应用内配置接口地址、密钥与模型即可；超长文本自动分段翻译并显示进度 |
| 应用 | 默认跟随系统语言（英文/简体中文）与系统亮暗色主题，也可手动指定；应用内更新检查指向本仓库；支持 TXT 与 DOCX 日志导出 |

## 扫描流程

1. 相机帧在独立线程上按受控频率分析，旧帧会被丢弃以保持预览流畅。
2. 轻量检测器实时绘制文档引导框，不会阻塞拍摄。
3. 拍摄后，ClearScan 在方向校正后的图像上运行更高分辨率的 OpenCV 检测器。
4. 检测到的四边形在透视校正前可任意调整。
5. 校正后的页面可增强、滤镜处理、重排顺序，并导出为图片或多页 PDF。

如果设备无法同时绑定 CameraX 的预览、拍摄与分析，ClearScan 会回退到预览+拍摄模式，而不是终止相机流程。

## 云端翻译

翻译由你选择的云端引擎完成，任何 OpenAI 兼容的 `/chat/completions` 接口均可使用：

- DeepSeek（`https://api.deepseek.com`，`deepseek-chat`）—— 默认配置
- OpenAI、Kimi（月之暗面）、通义（灵积）、OpenRouter、本地 Ollama 等

在翻译页面填入接口地址、API 密钥和模型名称即可开始翻译。密钥仅保存在本设备，且只发送给你配置的接口。超长文本会自动分段处理。

本版本已移除原先的本地 Hy-MT2 / llama.cpp 推理引擎：APK 体积缩减约 300 MB，构建也不再需要任何原生编译。

## 隐私说明

- 文档、页面图片、设置与日志全部保存在本地。
- ClearScan 不需要云账号。
- 扫描的文档不会被上传到任何服务器。
- 文件只在你主动分享、导出、打开链接或检查更新时离开应用。
- 若使用云端翻译，仅把你提交的文本发送到你自行配置的 API。
- 应用日志记录运行元数据与错误信息，不包含扫描页面内容。

## 兼容性

| 项目 | 值 |
| --- | --- |
| 最低 Android 版本 | Android 8.0（API 26） |
| 目标 Android 版本 | Android 16（API 36） |
| 公开 ABI | `arm64-v8a` |
| 构建 JDK | JDK 17 |
| Android SDK | SDK 36 |

不同厂商设备的相机能力存在差异，生产环境部署前建议在真机上验证。

## 安装

从 [Releases 页面](https://github.com/ant-cave/ClearScan/releases/latest) 下载签名 APK 与校验和：

- `ClearScan-vX.Y.Z-arm64-v8a.apk`
- `ClearScan-vX.Y.Z-arm64-v8a.apk.sha256`

本仓库的发布版本使用本仓库的发布证书签名。如果你从其他构建（例如原上游 APK）升级，请先导出重要文档、卸载旧版本，再安装本版本。

## 从源码构建

```bash
git clone https://github.com/ant-cave/ClearScan.git
cd ClearScan
./gradlew testDebugUnitTest :app:assembleDebug
```

Debug APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

构建不再需要安装 Android NDK 或 CMake —— 项目已不含任何原生源码。

## 正式签名

Release 构建通过环境变量读取签名材料：

```text
CLEARSCAN_KEYSTORE_PATH
CLEARSCAN_KEYSTORE_PASSWORD
CLEARSCAN_KEY_ALIAS
CLEARSCAN_KEY_PASSWORD
```

签名密钥绝不提交到仓库。推送 `v*` 标签会自动触发 [发布工作流](.github/workflows/release.yml)：运行单元测试、构建签名 Release APK，并连同 SHA-256 校验和一起自动发布为 GitHub Release。

## 项目结构

```text
app/src/main/java/com/clearscan/
  MainActivity.kt             Compose UI 与应用工作流
  DocumentEdgeDetector.kt     OpenCV 检测与透视校正
  DocumentFrameAnalyzer.kt    节流式 CameraX 实时帧分析
  ClearScanDatabase.kt        Room 实体、DAO 与迁移
  OverlayEditors.kt           水印与签名编辑器
  BarcodeAnalyzer.kt          ML Kit 二维码/条形码分析
  AppUpdater.kt               GitHub Release 更新流程
  SettingsRepository.kt       DataStore 应用设置
  LogExporter.kt              TXT 与 DOCX 日志导出
```

## 已知限制

- 公开 APK 目前仅支持 ARM64。
- PDF 编辑以页面为单位，不是 Acrobat 式的文本排版编辑器。
- 在受限的 Camera2 实现上，实时边缘引导可能回退到仅拍摄模式。
- 云端翻译的质量与时延取决于你配置的服务商，翻译需要网络连接。

## 参与贡献

欢迎提交 Bug 报告、可复现的相机日志、检测用例与聚焦的 Pull Request。提 Issue 前请附上 ClearScan 版本、Android 版本、设备型号、复现步骤，以及可导出的应用日志。

## 第三方软件

ClearScan 使用 CameraX、Jetpack Compose、Room、OpenCV 与 ML Kit。再分发本应用前请查阅相关第三方声明。

## 许可证

ClearScan（本版本）以 [GNU Affero 通用公共许可证 v3.0 或更高版本](LICENSE)（AGPL-3.0-or-later）发布。

本项目基于 [SuiYueMengHen 的 ClearScan](https://github.com/SuiYueMengHen/ClearScan)（原始许可证为 MIT）。按 MIT 条款要求，上游的 MIT 许可声明保留在 LICENSE 文件中。
