package id.co.erdigma.satudata.modules.collection.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import id.co.erdigma.satudata.annotation.CurrentUser;
import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.modules.collection.dto.CollectionResponse;
import id.co.erdigma.satudata.modules.collection.service.CollectionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/collections")
@Tag(name = "4. Koleksi", description = """
        Koleksi adalah **pengelompokan tematik beberapa dataset** yang saling berkaitan, bisa
        melintasi divisi penerbit — misalnya beberapa dataset berbeda yang sama-sama menceritakan
        kinerja komersial.

        Bedanya dengan topik: satu dataset bisa punya banyak topik, tapi paling banyak satu
        koleksi induk. Topik itu label, koleksi itu wadah.

        Saat ini baru ada satu koleksi berisi sedikit dataset — halaman koleksi di portal memang
        belum banyak isinya.
        """)
public class CollectionController {
    @Autowired
    private CollectionService collectionService;

    @GetMapping()
    @Operation(summary = "Daftar seluruh koleksi", description = """
            Semua koleksi beserta nama, keterangan, dan divisi pengelolanya.

            Ambil nilai `slug` dari sini untuk membuka detailnya.
            """)
    @ApiResponse(responseCode = "200", description = "Daftar koleksi berhasil diambil", useReturnTypeSchema = true)
    public ResponseEntity<List<CollectionResponse>> index(@CurrentUser User user) {
        return ResponseEntity.ok(collectionService.getAll());
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Ambil satu koleksi beserta dataset di dalamnya", description = """
            Keterangan lengkap satu koleksi berikut daftar dataset anggotanya.

            Seperti pada dataset, path parameternya **slug**, bukan UUID. Ambil nilainya dari
            `GET /api/v1/collections`.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Koleksi ditemukan", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "Slug tidak dikenal", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class))))
    })
    public ResponseEntity<CollectionResponse> getById(@CurrentUser User user,
            @Parameter(description = "Slug koleksi. Ambil dari GET /api/v1/collections.", example = "komersial", required = true) @PathVariable String slug) {
        return ResponseEntity.ok(collectionService.getBySlug(slug));
    }
}
