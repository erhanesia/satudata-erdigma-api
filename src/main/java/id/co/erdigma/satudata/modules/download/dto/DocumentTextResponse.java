package id.co.erdigma.satudata.modules.download.dto;

import java.util.List;

import lombok.Data;

/**
 * Isi dokumen Word dalam bentuk paragraf polos.
 *
 * Yang TIDAK ikut: tata letak, tabel, gambar, header, dan footer. Itu batas
 * nyata dari membaca .docx tanpa mesin render dokumen, dan antarmuka
 * menyebutkannya apa adanya — pembaca yang mengira sudah melihat dokumen
 * lengkap padahal belum akan mengambil keputusan dari setengah isi.
 */
@Data
public class DocumentTextResponse {
    private String fileName;
    private String label;
    private List<String> paragraphs;

    /** True bila dokumennya lebih panjang daripada yang dikirim. */
    private boolean truncated;
}
