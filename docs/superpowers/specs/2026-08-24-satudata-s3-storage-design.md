# Penyimpanan Berkas Dataset di AWS S3

Tanggal: 2026-08-24
Repo: `satudata-erdigma-api`

## Masalah

Berkas dataset saat ini ditulis ke folder pada disk mesin yang menjalankan
aplikasi (`LocalFileStorage`). Di produksi itu berarti berkas hilang setiap
kali kontainer diganti, dan tidak bisa dibagi antar instance. Portal butuh
penyimpanan yang bertahan di luar siklus hidup proses.

## Ruang lingkup

Hanya penyimpanan berkas dataset di sisi back-end. Hosting front-end statis di
S3 tidak termasuk dan tidak dirancang di sini.

## Keputusan yang sudah diambil

| Perkara | Keputusan |
|---|---|
| Lingkungan | S3 di semua lingkungan (dev dan prod), dipisah prefix kunci |
| Bucket | `erhanesia-files` — sama dengan hris-api |
| Region | `ap-southeast-1` |
| SDK | `software.amazon.awssdk:s3` versi `2.29.0`, selaras hris-api |
| Kredensial | `DefaultCredentialsProvider` bawaan SDK, tanpa properti kredensial |
| Jalur unduhan | Byte tetap mengalir lewat aplikasi, bukan presigned URL |
| Migrasi data | Tidak ada — belum ada berkas produksi |

## Arsitektur

Seam sudah ada. `FileStorage` adalah antarmuka; `LocalFileStorage` satu-satunya
implementasi hari ini. Integrasi S3 berarti menambah implementasi kedua dan
memilih salah satunya lewat properti — bukan mengubah kode bisnis.

Konsumen `FileStorage`:

- `modules/download/service/DownloadService` — `open()` untuk mengalirkan berkas
- `modules/status/service/StatusService` — `isHealthy()` untuk halaman Status Produk
- `modules/dataset/service/DatasetImportService` — **saat ini menyalahi seam**,
  meng-inject `LocalFileStorage` konkret dan memanggil `storeFrom(Path, ...)`
  yang bukan anggota antarmuka

Kebocoran di `DatasetImportService` wajib ditutup sebagai bagian pekerjaan ini.
Kalau dibiarkan, impor dataset diam-diam menulis ke disk lokal sementara
unduhan membaca dari S3 — berkas terdaftar di database tapi tidak pernah bisa
diunduh dari instance lain.

### Berkas baru

`src/main/java/id/co/erdigma/satudata/service/storage/S3FileStorage.java`

- `@Service`, `@ConditionalOnProperty(name = "satudata.storage.provider", havingValue = "S3", matchIfMissing = true)`
- `PROVIDER = "S3"` — tersimpan di kolom `dataset_resource.storage_provider`
  yang sudah ada, sehingga baris lama bernilai `LOCAL` tetap terbaca

### Suntingan

1. **`pom.xml`** — dependensi `software.amazon.awssdk:s3:2.29.0`.
2. **`LocalFileStorage`** — tambah
   `@ConditionalOnProperty(name = "satudata.storage.provider", havingValue = "LOCAL")`.
   Tanpa ini dua bean `FileStorage` bertabrakan dan aplikasi menolak start.
3. **`FileStorage`** — naikkan `storeFrom(Path source, String storageKey, String contentType)`
   menjadi anggota antarmuka dengan implementasi `default` yang membuka `Path`
   lalu mendelegasikan ke `store(InputStream, ...)`. `S3FileStorage`
   meng-override supaya `putObject` membaca langsung dari `Path` tanpa salinan
   tambahan.
4. **`DatasetImportService`** — ganti tipe field dan import dari
   `LocalFileStorage` menjadi `FileStorage`.

### Bentuk kunci

```
satudata/{stage}/dataset/{slug}/{namaBerkas}
```

`{stage}` diambil dari properti `custom.stage` yang sudah ada (`dev` di profil
dev, `prod` di produksi). Prefix dijahit di dalam `S3FileStorage`, tidak
disimpan ke database — `storageKey` di `dataset_resource` tetap relatif
(`dataset/{slug}/{namaBerkas}`) sehingga pindah bucket atau ganti skema prefix
tidak menuntut migrasi data.

## Konfigurasi

```yaml
satudata:
  storage:
    provider: ${STORAGE_PROVIDER:S3}
    s3:
      bucket: ${AWS_S3_BUCKET:erhanesia-files}
      region: ${AWS_REGION:ap-southeast-1}
```

Profil prod tidak perlu memuat blok ini; bawaannya sudah benar dan `custom.stage`
yang membedakan prefix.

### Kredensial

Tidak ada properti kredensial sama sekali. Klien dibangun dengan
`S3Client.builder().region(...).build()`, dan `DefaultCredentialsProvider`
bawaan SDK menyelesaikan kredensial berurutan: variabel lingkungan
`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, lalu `~/.aws/credentials`, lalu
IAM role instance.

Konsekuensinya: laptop yang sudah `aws configure` jalan apa adanya, penyebaran
dengan env var jalan, dan perpindahan ke EC2/ECS dengan IAM role nanti tidak
menuntut perubahan kode.

Ini **sengaja berbeda** dari hris-api, yang memakai `StaticCredentialsProvider`
membaca properti `aws.accessKeyId`/`aws.secretAccessKey`. Di hris-api nilai
kedua properti itu tertulis plaintext di `application.properties` dan sudah
ter-commit. Menyalin pola tersebut akan menggandakan kebocoran ke repo kedua.
Rotasi kunci hris-api adalah pekerjaan terpisah di luar spec ini.

## Penanganan galat

| Kondisi | Perilaku |
|---|---|
| `NoSuchKeyException` saat `open()` | `ResourceNotFoundException` — sama persis dengan `LocalFileStorage`, penanganan di `DownloadService` tak berubah |
| `S3Exception` / `SdkException` lain | `IllegalStateException` berpesan Indonesia, senada gaya berkas lokal |
| Bucket tak terjangkau | `isHealthy()` mengembalikan `false`; halaman Status Produk melaporkan DEGRADED |

`@PostConstruct` memanggil `isHealthy()` sekali dan menulis log WARN bila gagal.
Aplikasi **tetap start**. Alasannya: gagal-diam pada unggah lebih mahal daripada
start yang gagal, dan halaman Status Produk sudah menjadi tempat yang benar
untuk melaporkannya.

`storageKey` ditolak bila mengandung `..` atau diawali `/`. Kunci S3 memang
tidak bisa menembus keluar bucket, tetapi prefix `satudata/{stage}/` harus tetap
mengikat.

## Aliran byte

**Unggah** — `putObject` menuntut content-length, yang tidak diketahui dari
`InputStream`. Maka `store(InputStream, ...)` menyalin ke berkas sementara lebih
dulu sambil menghitung SHA-256 dan ukuran dalam satu lintasan, lalu `putObject`
dari `Path`, dan menghapus berkas sementara di blok `finally`. Batas 10MB
mengikuti `spring.servlet.multipart.max-file-size` yang sudah berlaku.

**Unduh** — `getObject` mengembalikan `ResponseInputStream` yang diteruskan apa
adanya ke respons. Byte melewati aplikasi, sehingga `DownloadLog` tetap berarti
"berkas benar-benar terunduh", bukan "tautan diterbitkan". Jaminan ini
dinyatakan eksplisit dalam komentar `FileStorage` dan sengaja dipertahankan.

## Pengujian

Satu berkas `S3FileStorageTest` dengan `S3Client` di-mock Mockito. Tanpa
jaringan, tanpa kredensial:

1. Kunci yang dikirim ke `putObject` memuat prefix `satudata/{stage}/`.
2. SHA-256 dan ukuran yang dihitung dari stream cocok dengan isi yang diberikan.
3. `NoSuchKeyException` dari `getObject` muncul sebagai `ResourceNotFoundException`.

Konteks pengujian Spring disetel `satudata.storage.provider=LOCAL` agar
`SatudataApplicationTests` tidak menyentuh AWS.

## Yang sengaja tidak dikerjakan

- **Presigned URL.** Lebih murah dan lebih cepat, tetapi mengubah makna
  `DownloadLog` dari bukti unduhan menjadi bukti penerbitan tautan. Untuk portal
  data, itu regresi audit. Tambahkan hanya jika biaya egress terbukti jadi
  masalah, dan pisahkan lognya.
- **`S3TransferManager` / unggah multipart.** Menambah `s3-transfer-manager`
  dan `aws-crt`. Batas unggah masih 10MB; belum ada berkas yang membutuhkannya.
- **Cache hasil `isHealthy()`.** Setiap kunjungan halaman Status Produk memicu
  satu `headBucket`. Caffeine sudah menjadi dependensi bila kelak perlu.
- **Migrasi berkas lokal ke S3.** Belum ada berkas produksi.
