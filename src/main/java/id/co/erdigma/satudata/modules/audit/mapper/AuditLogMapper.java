package id.co.erdigma.satudata.modules.audit.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import id.co.erdigma.satudata.modules.audit.dto.AuditLogResponse;
import id.co.erdigma.satudata.modules.audit.entity.AuditLog;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {
    AuditLogMapper INSTANCE = Mappers.getMapper(AuditLogMapper.class);

    /**
     * {@code actorCognitoId} sengaja tidak ikut. Itu identitas mesin dari
     * Cognito dan tidak berguna bagi pembaca layar; nama pelakunya sudah ada.
     */
    AuditLogResponse toResponse(AuditLog auditLog);
}
