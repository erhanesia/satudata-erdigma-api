package id.co.erdigma.satudata.modules.dataset.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import id.co.erdigma.satudata.modules.dataset.dto.DatasetColumnResponse;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResourceResponse;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponse;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponseLite;
import id.co.erdigma.satudata.modules.dataset.dto.FormatResponse;
import id.co.erdigma.satudata.modules.dataset.dto.TopicResponse;
import id.co.erdigma.satudata.modules.dataset.dto.UploaderResponse;
import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetColumn;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.entity.Format;
import id.co.erdigma.satudata.modules.dataset.entity.Topic;

@Mapper(componentModel = "spring")
public interface DatasetMapper {
    DatasetMapper INSTANCE = Mappers.getMapper(DatasetMapper.class);

    @Mapping(target = "topics", source = "topics", qualifiedByName = "toTopicNames")
    @Mapping(target = "formats", source = "formats", qualifiedByName = "toFormatNames")
    @Mapping(target = "resources", ignore = true)
    DatasetResponse toResponse(Dataset dataset);

    // resources dilekatkan belakangan oleh DatasetService lewat satu query
    // untuk seluruh halaman — bukan per baris.
    @Mapping(target = "topics", source = "topics", qualifiedByName = "toTopicNames")
    @Mapping(target = "formats", source = "formats", qualifiedByName = "toFormatNames")
    @Mapping(target = "resources", ignore = true)
    DatasetResponseLite toResponseLite(Dataset dataset);

    /**
     * {@code divisionCode} diambil dari relasi divisi milik user. Kalau user
     * belum terhubung ke divisi mana pun, MapStruct menghasilkan null dan panel
     * admin menampilkan "—" — bukan string kosong yang terlihat seperti bug.
     */
    @Mapping(target = "divisionCode", source = "division.code")
    UploaderResponse toUploaderResponse(User user);

    List<DatasetColumnResponse> toColumnResponseList(List<DatasetColumn> columns);

    @Mapping(target = "formatName", source = "format.name")
    DatasetResourceResponse toResourceResponse(DatasetResource resource);

    List<DatasetResourceResponse> toResourceResponseList(List<DatasetResource> resources);

    TopicResponse toTopicResponse(Topic topic);

    List<TopicResponse> toTopicResponseList(List<Topic> topics);

    FormatResponse toFormatResponse(Format format);

    List<FormatResponse> toFormatResponseList(List<Format> formats);

    @Named("toTopicNames")
    default List<String> toTopicNames(List<Topic> topics) {
        if (topics == null) {
            return null;
        }
        return topics.stream().map(Topic::getName).toList();
    }

    @Named("toFormatNames")
    default List<String> toFormatNames(List<Format> formats) {
        if (formats == null) {
            return null;
        }
        return formats.stream().map(Format::getName).toList();
    }
}
