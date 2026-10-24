# TikTok Live Popup (Fabric, Minecraft 26.3)

Turunan dari mod *Take Your Stream Chat* (lisensi CC0), dipangkas hanya untuk **TikTok**.

## Fitur
- Popup chat TikTok di **kiri bawah** layar (tanpa floating chat 3D).
- Notifikasi **follow**, **gift**, **fans** (fans club/subscribe), masing-masing bisa **sound custom**.
- Panel **viewer & likes realtime** (pojok bisa dipilih).
- Pengaturan lewat tombol `]` atau `/tiktok settings`.

## Build
Butuh JDK 25: `./gradlew build` → `build/libs/tiktok-live-popup-1.0.0+26.3.jar`
(atau push ke GitHub; workflow `.github/workflows/build.yml` membangun jar otomatis).

## Pemakaian
1. `/tiktok connect namauser` (atau isi username di pengaturan → Hubungkan). Jika belum live, mod menunggu otomatis.
2. Sound custom: taruh `.wav` / `.ogg` di `<folder game>/tiktok-live-popup/sounds/`, lalu di tab Follow/Gift/Fans klik tombol "File sound" untuk memilih. (mp3 tidak didukung, konversi dulu.)
3. Tes tanpa live: `/tiktok test chat|follow|gift|fans|stats`.
4. Jika notif fans tidak muncul: `/tiktok debug on`, lalu lihat `logs/latest.log` untuk nama event yang dikirim TikTok.

Perintah lain: `/tiktok disconnect`, `/tiktok status`, `/tiktok sounds`.
