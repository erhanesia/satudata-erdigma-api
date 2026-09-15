package id.co.erdigma.satudata.modules.dataset.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Menjaga perintah INSERT milik {@link DatasetRowRepositoryImpl} tetap
 * sejalan dengan tabel {@code dataset_row}.
 *
 * <h2>Kenapa perlu</h2>
 *
 * Sejak baris ditulis lewat JDBC, daftar kolomnya ditulis tangan, dan
 * Hibernate tidak lagi ikut memeriksanya. Tabel ini pernah berubah: changeset
 * 37 menambahkan {@code resource_id}. Kolom baru berikutnya yang boleh kosong
 * akan terisi kosong untuk setiap baris impor, tanpa satu pun galat.
 *
 * Daftarnya dibaca dari database yang sudah dimigrasi Liquibase, jadi tes ini
 * gagal begitu changeset baru menambah kolom tanpa memperbarui INSERT-nya.
 */
@SpringBootTest(properties = "satudata.storage.provider=LOCAL")
class DatasetRowInsertColumnsTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("perintah INSERT baris menyebut setiap kolom dataset_row selain id")
    void insertNamesEveryColumnExceptId() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name
                  FROM information_schema.columns
                 WHERE table_schema = current_schema()
                   AND table_name = 'dataset_row'
                   AND column_name <> 'id'
                """, String.class);

        assertThat(DatasetRowRepositoryImpl.INSERT_COLUMNS).containsExactlyInAnyOrderElementsOf(columns);
    }

    @Test
    @DisplayName("id baris dibuat database, jadi boleh tidak disebut")
    void idIsGeneratedByTheDatabase() {
        // Pasangan tes di atas. Kalau id kelak berhenti dibuat database,
        // mengecualikannya dari INSERT membuat setiap impor gagal.
        String identity = jdbcTemplate.queryForObject("""
                SELECT is_identity
                  FROM information_schema.columns
                 WHERE table_schema = current_schema()
                   AND table_name = 'dataset_row'
                   AND column_name = 'id'
                """, String.class);

        assertThat(identity).isEqualTo("YES");
    }
}
