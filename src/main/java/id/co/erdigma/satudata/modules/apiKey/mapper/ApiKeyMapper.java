package id.co.erdigma.satudata.modules.apiKey.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import id.co.erdigma.satudata.modules.apiKey.dto.ApiKeyResponse;
import id.co.erdigma.satudata.modules.apiKey.entity.ApiKey;

@Mapper(componentModel = "spring")
public interface ApiKeyMapper {
    ApiKeyMapper INSTANCE = Mappers.getMapper(ApiKeyMapper.class);

    @Mapping(target = "masked", ignore = true)
    ApiKeyResponse toResponse(ApiKey key);
}
