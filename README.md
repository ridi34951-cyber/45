# SonicShield 3.0.15 — Powered by Ridi

Android Studio project and GitHub Actions workflow.

## SonicShield 3.0.15 — jalur kompatibilitas konservatif

- Aplikasi tidak meminta Bluetooth SCO/HFP dan tidak mengubah Android ke mode panggilan.
- Input suara menggunakan mikrofon HP. Suara mic tidak otomatis dikirim ke speaker TWS.
- **Noise Monitor:** mic hanya dipakai untuk mengukur/menganalisis level kebisingan. Tidak dibuat AudioTrack dan tidak ada sampel mic yang dikirim ke TWS. Ini mencegah efek suara mic bercampur dengan musik. Mode ini bukan ANC.
- **Transparency:** hanya mode ini yang membuat jalur monitor mic ke output Bluetooth A2DP. Sinyal mic melewati high-pass, low-pass lembut, AGC, dan limiter sebelum dikirim ke TWS.
- **Off:** perekaman dan output monitor dihentikan.
- Pemeriksaan Bluetooth membedakan output A2DP dan LE Audio bila Android menyediakan API-nya. Jika TWS hanya muncul sebagai A2DP, aplikasi tidak menganggap codec SBC/AAC sebagai bukti adanya input mic LE Audio.
- UI menampilkan mode yang dipilih dan status proses. Ikon aplikasi bergaya earbuds futuristik.

## Batas kemampuan yang perlu diketahui

Software Reduce saat ini adalah **analisis kebisingan**, bukan ANC dan bukan pengubah suara musik aplikasi lain. Aplikasi Android biasa tidak dapat membuat gelombang anti-noise akustik yang membatalkan suara luar hanya dari satu mic HP. Untuk memproses musik dari aplikasi lain secara global dibutuhkan akses capture yang dibatasi Android dan tetap tidak menghasilkan ANC akustik. Karena itu mode Reduce sengaja tidak memutar suara mic dan tidak mengklaim menghilangkan suara luar.

Transparency adalah pemantauan suara sekitar dari mic HP. Karena output memakai Bluetooth A2DP, akan ada latensi yang bergantung pada TWS/ponsel. Filter dan AGC membantu mengatur suara, tetapi tidak menjamin semua suara kecil terdengar. Gunakan volume rendah saat pengujian, hentikan bila muncul feedback/dengung atau suara tidak nyaman, dan jangan mengandalkannya untuk mendengar peringatan lalu lintas.

## Build APK via GitHub Actions

1. Ekstrak ZIP.
2. Upload seluruh isi langsung ke root repository GitHub, termasuk folder `.github`.
3. Jalankan workflow **SonicShield Android Build** dari tab Actions.
4. Unduh APK dari bagian Artifacts setelah workflow selesai.

## Build configuration

- Android Gradle Plugin 8.9.2
- Kotlin 2.0.21
- Gradle 8.11.1
- JDK 17
- compileSdk 35, minSdk 26, targetSdk 35


## Keputusan kompatibilitas ANC

Aplikasi tidak memaksa HFP/SCO, tidak mengirim suara mic mentah, dan tidak mengaku memiliki ANC ketika perangkat tidak menyediakan jalur pembatalan. Pada Bluetooth Classic, input mic TWS umumnya terkait jalur komunikasi; LE Audio memerlukan dukungan pada ponsel **dan** earbuds. Deteksi LE Audio di sini hanya diagnostik kemampuan/rute, bukan jaminan mic TWS dapat diakses. Kontrol ANC bawaan TWS juga memerlukan protokol vendor yang belum diketahui untuk A9 Pro, JBL Phantom, dan Goojodoq J201.

Karena itu build ini mempertahankan Transparency yang sudah bekerja melalui mic HP dan A2DP, sementara mode Noise Monitor hanya mengukur suara sekitar. ANC akustik 40% tidak dapat dijanjikan atau dihasilkan secara kompatibel dari kombinasi perangkat yang informasinya tersedia saat ini.


## Hardware ANC Detection (3.0.15)

The app now checks the connected Bluetooth audio route (A2DP / LE Audio) and the phone's LE Audio capability. The result is deliberately conservative: Android does not provide a universal public API for reading or toggling vendor-specific earbud ANC. LE Audio detection is not proof of ANC, and A2DP/SBC/AAC or a product name cannot prove ANC is absent or present. The app therefore never labels hardware ANC as active unless a real vendor control integration is available.
