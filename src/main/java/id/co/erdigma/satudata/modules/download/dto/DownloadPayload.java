package id.co.erdigma.satudata.modules.download.dto;

import java.io.InputStream;

import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Pembawa hasil unduh: metadata berkas + aliran byte-nya. */
@Getter
@AllArgsConstructor
public class DownloadPayload {
    private final DatasetResource resource;
    private final InputStream content;
}
