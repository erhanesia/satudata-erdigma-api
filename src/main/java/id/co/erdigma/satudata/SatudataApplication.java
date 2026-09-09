package id.co.erdigma.satudata;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SatudataApplication {

	/*
	  Jam server dipatok UTC, sebelum apa pun sempat berjalan.
	
	  Seluruh stempel waktu di aplikasi ini dibuat lewat LocalDateTime.now(),
	  yang mengambil jam dinding zona bawaan JVM dan TIDAK ikut menyimpan
	  zonanya. Kolomnya pun timestamp without time zone. Jadi arti angka yang
	  tersimpan sepenuhnya ditentukan zona mesin yang kebetulan menjalankannya.
	
	  Di produksi itu UTC, tetapi UTC-nya kebetulan: amazoncorretto:17-alpine
	  memang begitu bawaannya, dan Dockerfile tidak pernah menyatakan apa-apa
	  soal zona. Di laptop pengembang yang WIB, kode yang sama menulis angka
	  yang artinya lain. Satu baris TZ yang ditambahkan orang lain di kemudian
	  hari sudah cukup menggeser seluruh riwayat, tanpa satu pun galat muncul.
	
	  Dipatok di sini, bukan di Dockerfile, supaya laptop dan produksi tunduk
	  pada aturan yang sama. Perbaikan tampilan jam di front-end bertumpu pada
	  janji bahwa yang dikirim server itu UTC; janji itu perlu ditepati juga
	  saat dijalankan di lokal, kalau tidak ia tidak bisa diuji sebelum naik.
	
	  Sebelum SpringApplication.run, karena bean yang dibuat setelahnya sudah
	  boleh membaca jam.
	*/
	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(SatudataApplication.class, args);
	}

}
