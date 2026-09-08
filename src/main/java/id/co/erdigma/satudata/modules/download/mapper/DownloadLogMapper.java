package id.co.erdigma.satudata.modules.download.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import id.co.erdigma.satudata.modules.download.dto.DownloadLogResponse;
import id.co.erdigma.satudata.modules.download.entity.DownloadLog;

@Mapper(componentModel = "spring")
public interface DownloadLogMapper {
    DownloadLogMapper INSTANCE = Mappers.getMapper(DownloadLogMapper.class);

    DownloadLogResponse toResponse(DownloadLog downloadLog);
}
