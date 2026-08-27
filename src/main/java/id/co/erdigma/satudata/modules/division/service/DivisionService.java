package id.co.erdigma.satudata.modules.division.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.modules.division.dto.DivisionResponse;
import id.co.erdigma.satudata.modules.division.entity.Division;
import id.co.erdigma.satudata.modules.division.mapper.DivisionMapper;
import id.co.erdigma.satudata.modules.division.repository.DivisionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DivisionService {
    @Autowired
    private DivisionRepository divisionRepository;
    @Autowired
    private DivisionMapper divisionMapper;

    /**
     * Unduhan tidak lagi datang dari kolom {@code division.downloads} — kolom itu
     * dicabut di changeset 43 karena tidak ada yang pernah menulisinya. Query-nya
     * mengembalikan pasangan {@code [Division, Long]}, dan jumlahnya dipasangkan
     * ke DTO di sini.
     *
     * MapStruct sengaja tidak dipakai untuk ruas ini: {@code downloads} bukan
     * lagi properti entitas, jadi tidak ada yang bisa dipetakan otomatis.
     */
    @Transactional(readOnly = true)
    public List<DivisionResponse> getAll() {
        return divisionRepository.findAllWithDownloads().stream()
                .map(baris -> {
                    DivisionResponse response = divisionMapper.toResponse((Division) baris[0]);
                    response.setDownloads(((Number) baris[1]).longValue());
                    return response;
                })
                .toList();
    }
}
