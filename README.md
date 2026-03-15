# Tugas Besar 1 IF2211 Strategi Algoritma - Kelompok beeOL
Pemanfaatan Algoritma Greedy dalam pembuatan bot permainan Battlecode 2025.

## Deskripsi Singkat Algoritma Greedy
Bot tim kami mengimplementasikan berbagai algoritma *greedy* yang berfokus pada objektif permainan untuk mewarnai peta sebanyak mungkin dan memenangkan teritori. Setiap tipe robot memiliki fungsi *heuristic* yang unik sesuai dengan perannya di peta:

### 1. Soldier (Ekspansi & Pembangunan Menara)
- **Objektif:** Memperluas wilayah *(paint)* dan mengamankan reruntuhan *(ruins)*.
- **Strategi Greedy:** Pemilihan arah bergerak (fungsi seleksi) didasarkan pada skor perhitugan *greedy* yang memaksimalkan penemuan petak kosong/belum diwarnai (`+12` per *unpainted tile*) dikurangi biaya *paint cost* (`-15` per langkah). Robot juga memiliki penalti kuat pada petak yang sudah dikunjungi (mencegah *looping* pergerakan) dan penalti jika stok cat sedang sedikit. Jika bersebelahan dengan *ruins*, Soldier secara eksklusif akan memprioritaskan penyelesaian pola *tower*.

### 2. Mopper (Pembersih Area & Penyerang)
- **Objektif:** Membersihkan cat musuh, mengganggu pergerakan musuh, serta *support* area.
- **Strategi Greedy:** Apabila dihadapkan dengan pilihan untuk memukul (*mop swing*), Mopper secara *greedy* akan mengayunkan alatnya jika dapat mengenai minimal 2 musuh di arah ayunan. Untuk pergerakan, The Mopper memiliki skor *greedy* yang memaksimalkan langkah ke arah petak yang memiliki cat musuh (`+20`) atau berada di dekat posisi robot musuh (`+25`), sambil menghindari petak yang sudah diwarnai sekutu (`-15`).

### 3. Splasher (Pewarna Area Massal)
- **Objektif:** Mendominasi daerah dengan kemampuan *Area of Effect* (AoE) cat.
- **Strategi Greedy:** Dalam penyerangan, Splasher mengevaluasi seluruh petak dalam radius serangannya. Skor target dihitung dari seberapa banyak cat musuh yang bisa ditimpa (`+4` per petak), seberapa banyak petak kosong yang bisa diwarnai (`+2`), dan dikurangi jika hal tersebut menimpa cat sekutu (`-2`). Splasher akan *greedy* memilih target dengan skor tertinggi.

### 4. Tower (Pertahanan & Produksi Berbasis Fase)
- **Objektif:** Bertahan dari gempuran musuh sambil memproduksi unit yang sesuai dengan kebutuhan medan tempur secara efisien.
- **Strategi Greedy:** Menara memilih unit yang akan di-*spawn* berdasarkan kondisi lingkungan (secara reaktif/ *greedy*). Jika cat musuh di sekeliling sangat banyak (ancaman besar), ia memproduksi Mopper. Sebaliknya, menara akan memproduksi Soldier/Splasher di awal permainan untuk ekspansi. Lokasi *spawning* juga dipilih berdasarkan petak yang paling banyak terpengaruh oleh dominasi musuh (memprioritaskan pertahanan langsung di titik kontak musuh).

## Requirement Program
- **Java**: HARUS versi 21.
- **Gradle:** Digunakan untuk *build system*, sudah tersedia melalui `gradlew` pada repositori ini.

## Cara Build & Run Program
1. Buka terminal dan masuk ke root directory dari proyek ini.
2. Jalankan perintah *build* menggunakan Gradle wrapper:
   ```bash
   ./gradlew build
   ```
3. Pindah ke direktori *client* untuk menjalankan *visualizer*:
   ```bash
   cd client
   ```
4. Jalankan aplikasi klien/visualizer.
5. Setelah aplikasi visualizer terbuka, pilih direktori proyek ini (direktori root `Tubes1_beeOL`, **bukan** sub-direktori `src`) untuk memuat *bot* ke dalam *visualizer*.
6. Pilih bot `alternativeBots1` atau bot lainnya dari antarmuka permainan dan jalankan *match*.

## Anggota Kelompok
**Kelompok beeOL:**
1. Reinhard Alfonzo Hutabarat - 13524056
2. Arghawisesa Dwinanda Arham - 13524100
3. Amanda Aurellia Salsabilla - 13524131
