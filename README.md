# CaptureKit

<p align="center">
  <a href="https://jitpack.io/#govindtank/capture-improvement-kotlin"><img src="https://jitpack.io/v/govindtank/capture-improvement-kotlin.svg?style=flat-square" alt="JitPack"></a>
  <a href="https://github.com/govindtank/capture-improvement-kotlin/actions"><img src="https://img.shields.io/github/actions/workflow/status/govindtank/capture-improvement-kotlin/build.yml?branch=main&style=flat-square&label=build" alt="Build Status"></a>
  <img src="https://img.shields.io/badge/Platform-Android%20Native-brightgreen?style=flat-square" alt="Platform">
  <img src="https://img.shields.io/badge/API-23%2B-blue?style=flat-square" alt="API Level">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-green.svg?style=flat-square" alt="License"></a>
  <a href="https://github.com/govindtank"><img src="https://img.shields.io/badge/Author-Govind%20Tank-orange?style=flat-square" alt="Author"></a>
</p>

<p align="center">
  <b>Android CameraX intelligent capture toolkit — blur detection, angle matching, and orientation guidance.</b><br>
  <i>Architected &amp; Crafted with ❤️ by <a href="https://github.com/govindtank">Govind Tank</a></i>
</p>

---

## ⚡ Use Case

CaptureKit was designed for **re-photography** workflows — scenarios where a user needs to capture the same scene from the same angle as a previously taken reference photo. For example:

- 🏨 **Hotel room inspection** — Admin captures reference photos, cleaning staff re-captures after work
- 🏗️ **Construction progress** — Periodic photos from identical angles for comparison
- 📋 **Insurance claims** — Before/after damage documentation
- 🏠 **Real estate** — Consistent property listing photos

---

## 🔍 Features

| Feature | Description |
| :--- | :--- |
| 🔍 **Blur Detection** | Laplacian variance + Tenengrad — pure Kotlin, zero dependencies, ~2-4ms |
| 📐 **Angle Matching** | Hybrid sensor + OpenCV feature matching for precise alignment guidance |
| 📱 **Orientation Detection** | Real-time portrait/landscape detection via hardware sensors |
| 🎯 **Visual Guidance** | Ghost overlay, alignment arrows, artificial horizon HUD |
| 📊 **Quality Gate** | Configurable pass/fail criteria with detailed feedback |
| 📷 **CameraX Integration** | Drop-in `ImageAnalysis.Analyzer` with rate-limited frame processing |

---

## 📦 Installation

Add the JitPack repository and dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.govindtank:capture-improvement-kotlin:1.0.0")
}
```

---

## 💖 Support & Sponsorship

If you find this library helpful for your Android applications, consider supporting continuous development:

<p align="left">
  <a href="https://www.patreon.com/govindtank"><img src="https://img.shields.io/badge/Patreon-Support%20Creator-F96854?style=for-the-badge&logo=patreon&logoColor=white" alt="Patreon"></a>
  <a href="https://github.com/sponsors/govindtank"><img src="https://img.shields.io/badge/GitHub%20Sponsors-Sponsor-EA4AAA?style=for-the-badge&logo=github&logoColor=white" alt="GitHub Sponsors"></a>
  <a href="https://buymeacoffee.com/govindtank"><img src="https://img.shields.io/badge/Buy%20Me%20A%20Coffee-Donate-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black" alt="Buy Me A Coffee"></a>
</p>

- **Patreon**: [patreon.com/govindtank](https://www.patreon.com/govindtank)
- **GitHub Sponsors**: [github.com/sponsors/govindtank](https://github.com/sponsors/govindtank)
- **Buy Me a Coffee**: [buymeacoffee.com/govindtank](https://buymeacoffee.com/govindtank)

---

## 👨💻 Author

**Govind Tank**
- **GitHub**: [@govindtank](https://github.com/govindtank)
- **Website**: [govindtank.github.io](https://govindtank.github.io)
- **LinkedIn**: [linkedin.com/in/govind-tank](https://linkedin.com/in/govind-tank)

---

## 📄 License

Apache License 2.0
