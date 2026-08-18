package id.co.erdigma.satudata.modules.division.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import id.co.erdigma.satudata.modules.division.dto.DivisionResponse;
import id.co.erdigma.satudata.modules.division.dto.DivisionResponseLite;
import id.co.erdigma.satudata.modules.division.entity.Division;

@Mapper(componentModel = "spring")
public interface DivisionMapper {
    DivisionMapper INSTANCE = Mappers.getMapper(DivisionMapper.class);

    DivisionResponse toResponse(Division division);

    DivisionResponseLite toResponseLite(Division division);

    List<DivisionResponse> toResponseList(List<Division> divisions);
}
