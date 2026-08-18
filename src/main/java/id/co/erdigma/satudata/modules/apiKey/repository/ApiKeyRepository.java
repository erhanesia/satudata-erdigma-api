package id.co.erdigma.satudata.modules.apiKey.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.apiKey.entity.ApiKey;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    Optional<ApiKey> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    Optional<ApiKey> findByKeyHashAndDeletedAtIsNull(String keyHash);
}
