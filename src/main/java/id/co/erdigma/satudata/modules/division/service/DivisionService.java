package id.co.erdigma.satudata.modules.division.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.modules.division.dto.DivisionResponse;
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

    @Transactional(readOnly = true)
    public List<DivisionResponse> getAll() {
        return divisionMapper.toResponseList(divisionRepository.findAllByDeletedAtIsNullOrderByApiCallsDesc());
    }
}
