package id.co.erdigma.satudata.converter;

import java.lang.reflect.Field;
import java.util.UUID;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Menulis UUID sebagai {@code awalan-uuid} sesuai anotasi di field-nya.
 *
 * Ditulis untuk API <strong>Jackson 3</strong> ({@code tools.jackson}), bukan
 * Jackson 2 ({@code com.fasterxml.jackson.databind}). Spring Boot 4 memakai
 * Jackson 3, sementara Jackson 2 masih ikut terbawa sebagai dependensi
 * transitif — sehingga serializer yang ditulis untuk API lama tetap
 * <em>terkompilasi dengan sukses</em> lalu diabaikan diam-diam saat berjalan.
 * Gejalanya persis seperti fitur yang tidak pernah dipasang.
 *
 * Padanan namanya berubah: {@code JsonSerializer} → {@link ValueSerializer},
 * {@code SerializerProvider} → {@link SerializationContext}, dan antarmuka
 * {@code ContextualSerializer} lenyap karena {@code createContextual} kini
 * method biasa di kelas dasarnya.
 *
 * Kenapa perlu kontekstual: awalannya berbeda-beda per field, sementara Jackson
 * membuat satu instance serializer per tipe. Lewat {@code createContextual},
 * Jackson memberi tahu field mana yang sedang ditulis, sehingga instance yang
 * tepat dibuat sekali lalu dipakai ulang untuk field itu.
 *
 * Kenapa di lapisan Jackson dan bukan di mapper: seluruh DTO tetap memegang
 * {@code UUID}, jadi tidak ada mapper, service, maupun kode uji yang perlu tahu
 * soal awalan. Menambah awalan ke DTO baru cukup satu baris anotasi.
 */
public class PrefixedIdSerializer extends ValueSerializer<UUID> {

    private final IdPrefix prefix;

    /** Dipakai Jackson saat mendaftarkan serializer; belum tahu field-nya. */
    public PrefixedIdSerializer() {
        this(null);
    }

    private PrefixedIdSerializer(IdPrefix prefix) {
        this.prefix = prefix;
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        if (property == null) {
            return this;
        }
        IdPrefix ditemukan = cariAnotasi(property);
        if (ditemukan == null) {
            // Tidak dibiarkan lolos diam-diam. Kalau anotasinya tidak terbaca,
            // yang keluar adalah UUID polos — persis seperti sebelum fitur ini
            // ada, sehingga cacatnya tidak terlihat sampai ada yang
            // membandingkan keluaran dengan dokumentasi.
            throw new IllegalStateException(
                    "@PrefixedId tidak terbaca pada properti \"" + property.getName()
                            + "\" di " + property.getMember().getDeclaringClass().getName());
        }
        return new PrefixedIdSerializer(ditemukan);
    }

    /**
     * Mencari anotasi lewat tiga jalur, berurut dari yang paling murah.
     *
     * Jalur ketiga berguna karena DTO memakai Lombok {@code @Data}: getter-nya
     * dibangkitkan tanpa membawa anotasi apa pun dari field, jadi untuk properti
     * yang anggota utamanya getter, dua jalur pertama bisa menemui jalan buntu
     * walaupun anotasinya jelas tertulis di field.
     */
    private IdPrefix cariAnotasi(BeanProperty property) {
        PrefixedId annotation = property.getAnnotation(PrefixedId.class);
        if (annotation != null) {
            return annotation.value();
        }
        annotation = property.getContextAnnotation(PrefixedId.class);
        if (annotation != null) {
            return annotation.value();
        }
        Class<?> pemilik = property.getMember() == null ? null
                : property.getMember().getDeclaringClass();
        while (pemilik != null && pemilik != Object.class) {
            try {
                Field field = pemilik.getDeclaredField(property.getName());
                PrefixedId dariField = field.getAnnotation(PrefixedId.class);
                if (dariField != null) {
                    return dariField.value();
                }
            } catch (NoSuchFieldException ignored) {
                // Coba kelas induknya — DTO warisan menaruh id di kelas dasar.
            }
            pemilik = pemilik.getSuperclass();
        }
        return null;
    }

    @Override
    public void serialize(UUID value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(prefix.format(value));
    }
}
