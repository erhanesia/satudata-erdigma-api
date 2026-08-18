package id.co.erdigma.satudata.modules.status.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.status.dto.StatusResponse;
import id.co.erdigma.satudata.modules.status.entity.Incident;
import id.co.erdigma.satudata.modules.status.repository.IncidentRepository;
import id.co.erdigma.satudata.service.storage.FileStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Menyusun halaman Status Produk.
 *
 * Tiap komponen diperiksa sendiri-sendiri, bukan diturunkan dari satu status
 * global — supaya kalau hanya penyimpanan yang bermasalah, halaman ini bisa
 * menunjukkannya tanpa menyatakan seluruh portal tumbang.
 *
 * "Real-time API" dilaporkan belum tersedia karena memang belum dibangun.
 * Melaporkannya "Operasional" akan menyesatkan.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatusService {

    private static final String OPERATIONAL = "Operasional";
    private static final String DEGRADED = "Gangguan";
    private static final String NOT_AVAILABLE = "Belum tersedia";

    @Autowired
    private IncidentRepository incidentRepository;
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private FileStorage fileStorage;

    @Transactional(readOnly = true)
    public StatusResponse getStatus() {
        boolean databaseUp = isDatabaseUp();
        boolean storageUp = fileStorage.isHealthy();

        List<StatusResponse.Component> components = new ArrayList<>();
        // Portal menjawab permintaan ini, jadi lapisan web-nya sudah pasti hidup.
        components.add(component("Portal web", OPERATIONAL));
        components.add(component("Dataset / Collection API", databaseUp ? OPERATIONAL : DEGRADED));
        components.add(component("Real-time API", NOT_AVAILABLE));
        components.add(component("Datastore & unduhan",
                databaseUp && storageUp ? OPERATIONAL : DEGRADED));

        boolean allUp = databaseUp && storageUp;

        StatusResponse response = new StatusResponse();
        response.setOverall(allUp ? "UP" : "DEGRADED");
        response.setOverallLabel(allUp
                ? "Semua sistem beroperasi normal"
                : "Ada gangguan pada sistem");
        response.setComponents(components);
        response.setIncidents(incidentRepository.findAllByDeletedAtIsNullOrderByOccurredAtDesc()
                .stream().map(this::toItem).toList());
        return response;
    }

    private boolean isDatabaseUp() {
        try {
            datasetRepository.countByDeletedAtIsNull();
            return true;
        } catch (RuntimeException e) {
            log.warn("Pemeriksaan database gagal", e);
            return false;
        }
    }

    private StatusResponse.Component component(String name, String state) {
        StatusResponse.Component component = new StatusResponse.Component();
        component.setName(name);
        component.setState(state);
        return component;
    }

    private StatusResponse.IncidentItem toItem(Incident incident) {
        StatusResponse.IncidentItem item = new StatusResponse.IncidentItem();
        item.setId(incident.getId());
        item.setTitle(incident.getTitle());
        item.setTag(incident.getTag());
        item.setDetail(incident.getDetail());
        item.setOccurredLabel(incident.getOccurredLabel());
        item.setOccurredAt(incident.getOccurredAt());
        return item;
    }
}
