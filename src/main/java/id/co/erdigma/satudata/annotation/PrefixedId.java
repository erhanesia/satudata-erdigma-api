package id.co.erdigma.satudata.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;

import id.co.erdigma.satudata.converter.PrefixedIdSerializer;
import id.co.erdigma.satudata.enums.IdPrefix;

import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Menandai field UUID agar ditampilkan sebagai {@code awalan-uuid} di respons.
 *
 * Dipasang di DTO, bukan di entity. Entity tetap memegang UUID polos supaya
 * perbandingan, pencarian, dan relasi antar tabel tidak perlu tahu apa-apa soal
 * awalan.
 *
 * Perhatikan asal kedua anotasi bawaannya: {@code @JacksonAnnotationsInside}
 * tetap dari {@code com.fasterxml.jackson.annotation} (Jackson 3 memang masih
 * memakai paket anotasi lama), sedangkan {@code @JsonSerialize} <strong>harus
 * dari {@code tools.jackson.databind.annotation}</strong>. Memakai
 * {@code @JsonSerialize} milik Jackson 2 tetap terkompilasi tapi diabaikan
 * sepenuhnya saat berjalan.
 *
 * <pre>
 * &#64;PrefixedId(IdPrefix.DATASET)
 * private UUID id;   // keluar sebagai "dst-e0000000-0000-4000-8000-000000000009"
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@JacksonAnnotationsInside
@JsonSerialize(using = PrefixedIdSerializer.class)
public @interface PrefixedId {
    IdPrefix value();
}
