package id.co.erdigma.satudata.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.SQLDelete;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.division.entity.Division;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Bayangan lokal dari karyawan HRIS — bukan sumber kebenaran. Identitasnya
 * adalah {@code cognitoId} (klaim {@code sub} pada token Cognito), bukan
 * kolom {@code id}, supaya rujukan tetap sah setelah integrasi HRIS.
 */
@Data
@Entity
@SQLDelete(sql = "UPDATE users SET deleted_at=CURRENT_TIMESTAMP WHERE id = ?")
@DynamicUpdate
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = true)
    private String cognitoId;
    private String email;
    private String name;
    private String position;

    /** Peran portal — dasar otorisasi Satu Data. */
    @Enumerated(EnumType.STRING)
    private Role role;

    /** Hasil hitungan HRIS, direkam untuk pemetaan dan penelusuran. */
    @Enumerated(EnumType.STRING)
    @Column(name = "hris_permission_level")
    private HrisPermissionLevel hrisPermissionLevel;

    /**
     * Peran yang ditunjuk manusia lewat panel manajemen pengguna. Null berarti
     * baris ini mengikuti HRIS sepenuhnya.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role_override")
    private Role roleOverride;

    /** Id pengguna yang menunjuk. Tanpa foreign key — lihat changeset 00024. */
    @Column(name = "role_override_by")
    private UUID roleOverrideBy;

    @Column(name = "role_override_at")
    private LocalDateTime roleOverrideAt;

    /** Bahan mentah asal HRIS menurunkan tingkat izin, mis. "Manager". */
    private String jobLevel;

    /**
     * Pengenal posisi milik HRIS, dipakai mencocokkan aturan akses bertipe
     * POSITION.
     *
     * UUID, bukan nama, meski {@code position} di atas sudah menyimpan namanya.
     * Tabel posisi HRIS memuat salah ketik seperti "HO Customer Acquisiton" dan
     * "Sales & Complience Manager"; begitu diperbaiki di sana, pembatasan
     * berbasis nama putus tanpa galat apa pun — dataset sekadar berhenti
     * terlihat oleh orang yang seharusnya berhak. UUID tidak ikut berubah saat
     * namanya dirapikan.
     *
     * Kosong berarti pemiliknya tidak pernah cocok dengan aturan POSITION mana
     * pun. Gagal ke arah menutup, bukan membuka.
     */
    @Column(name = "hris_position_id")
    private UUID hrisPositionId;

    /**
     * Pengenal karyawan milik HRIS, dipakai mencocokkan aturan akses bertipe
     * EMPLOYEE — yaitu ketika sebuah dataset menunjuk orang tertentu secara
     * langsung, tanpa memedulikan jabatannya.
     *
     * Berbeda dari {@code cognitoId}, yang menyatakan identitas untuk masuk.
     * Satu orang bisa saja berganti akun Cognito tanpa berganti data
     * kekaryawanan, dan aturan akses seharusnya mengikuti orangnya.
     */
    @Column(name = "hris_employee_id")
    private UUID hrisEmployeeId;

    /**
     * Letak foto profil di S3, apa adanya seperti dikirim HRIS — mis.
     * {@code /hris/dev/profile-image/230425-0808.webp}.
     *
     * Path, bukan URL. Ruas {@code dev} di tengahnya berganti jadi {@code prod}
     * di produksi, dan nama bucket maupun region-nya bisa berubah kalau
     * infrastrukturnya dipindah; menyimpan URL utuh berarti setiap baris ikut
     * memuat pengetahuan itu dan harus ditulis ulang massal saat berubah.
     *
     * Kosong berarti karyawannya belum pernah mengunggah foto. Antarmuka
     * menampilkan inisial namanya sebagai ganti, seperti sebelum kolom ini ada.
     */
    @Column(name = "profile_image", length = 255)
    private String profileImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "division_id", nullable = true)
    @Fetch(FetchMode.SELECT)
    private Division division;

    public UUID getDivisionId() {
        return (division != null) ? division.getId() : null;
    }

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @PreRemove
    public void preventDelete() {
        if (deletedAt != null) {
            throw new ResourceNotFoundException("Data Already Been Deleted");
        }
    }
}
