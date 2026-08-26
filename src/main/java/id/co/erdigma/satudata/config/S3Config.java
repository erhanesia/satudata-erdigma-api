package id.co.erdigma.satudata.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Klien S3 sengaja dibangun TANPA properti kredensial apa pun.
 * DefaultCredentialsProvider bawaan SDK menyelesaikannya berurutan: variabel
 * lingkungan AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY, lalu ~/.aws/credentials,
 * lalu IAM role instance.
 *
 * Akibatnya laptop yang sudah `aws configure`, penyebaran dengan variabel
 * lingkungan, dan perpindahan ke EC2/ECS nanti sama-sama jalan tanpa perubahan
 * kode — dan tidak ada rahasia yang bisa ikut ter-commit. Ini berbeda dari
 * hris-api, yang menaruh access key plaintext di application.properties.
 */
@Configuration
@ConditionalOnProperty(name = "satudata.storage.provider", havingValue = "S3")
public class S3Config {

    @Bean
    public S3Client s3Client(@Value("${satudata.storage.s3.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
