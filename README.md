# Lilith-MV 🎵🎬

**Lilith-MV** adalah aplikasi pemutar musik dan video native (*native music and video player*) yang dibangun 100% menggunakan bahasa pemrograman **Kotlin** dengan sistem build Gradle modern. Proyek ini diinisialisasi dari template `google-gemini/aistudio-repository-template` untuk menghadirkan pengalaman pemutaran media lokal yang responsif, intuitif, dan performan.

---

## 🌟 Fitur Utama

- **Pemutar Musik & Video Native**: Mendukung pemutaran file audio (musik) dan video secara langsung (*native*) pada perangkat.
- **Tampilan Perpustakaan Video Fleksibel (List & Grid View)**: Memungkinkan pengguna beralih (*toggle*) antara tampilan daftar (*List View*) dan tampilan kisi (*Grid View*) pada perpustakaan video.
- **Antarmuka Responsif (Responsive UI)**: Penyesuaian tata letak responsif dengan *bottom content padding* yang optimal untuk navigasi dan berbagai ukuran layar perangkat.
- **Desain UI Modern**: Peningkatan antarmuka pengguna yang bersih dan terstruktur secara konsisten.
- **Konfigurasi Lingkungan Terintegrasi**: Dukungan file templat konfigurasi lingkungan untuk pengelolaan variabel lingkungan aplikasi.

---

## 🛠️ Teknologi & Spesifikasi Proyek

- **Bahasa Pemrograman**: Kotlin (100%)
- **Sistem Build**: Gradle dengan Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`)
- **Struktur Kode & Berkas**:
  - `app/` — Direktori utama modul aplikasi Android (UI, pemutar media, dan logika aplikasi).
  - `gradle/` — Pembungkus dan dependensi build Gradle wrapper.
  - `public/assets/aistudio` — Aset visual dan sumber daya media aplikasi.
  - `metadata.json` — Berkas metadata konfigurasi proyek.
  - `.env.example` — Berkas templat konfigurasi variabel lingkungan.
- **Template Dasar**: Generated from `google-gemini/aistudio-repository-template`.

---

## 📁 Struktur Repositori

```text
Lilith-MV/
├── app/                        # Modul dan kode sumber utama aplikasi (Kotlin)
├── gradle/                     # Konfigurasi Gradle wrapper
├── public/assets/aistudio/     # Berkas aset visual dan proyek AI Studio
├── .env.example                # Templat variabel lingkungan
├── .gitignore                  # Berkas pengecualian Git
├── build.gradle.kts            # Konfigurasi build Gradle utama (Kotlin DSL)
├── gradle.properties           # Properti konfigurasi build Gradle
├── metadata.json               # Metadata proyek
└── settings.gradle.kts         # Pengaturan repositori dan modul Gradle
```

---

## 🚀 Cara Menginstal & Menjalankan Proyek

### Prasyarat
- **Android Studio** (disarankan versi terbaru)
- **JDK 17** atau versi yang sesuai
- **Android SDK** (API level sesuai target build)

### Langkah-langkah
1. **Klon Repositori**:
   ```bash
   git clone <repository-url>
   cd Lilith-MV
   ```

2. **Konfigurasi Variabel Lingkungan**:
   Salin file templat `.env.example` menjadi `.env` sesuai kebutuhan konfigurasi lokal Anda.

3. **Build & Jalankan**:
   - Buka folder proyek di **Android Studio**.
   - Biarkan Gradle melakukan sinkronisasi dependensi secara otomatis.
   - Kompilasi dan jalankan aplikasi menggunakan Gradle wrapper:
     ```bash
     ./gradlew assembleDebug
     ```

---

## 🏷️ Versi & Rilis

- **Rilis Terbaru**: `v1.1.0` (Latest Release)
- Memiliki 2 kategasi rilis utama pada repositori.

---

## 👥 Kontributor

- **Itsnanzzz (Annan)** — Pemilik repositori & pengembang utama
- **Claude** — Kontributor

---
*Dokumentasi ini disusun secara otomatis berdasarkan repositori resmi Lilith-MV.*
