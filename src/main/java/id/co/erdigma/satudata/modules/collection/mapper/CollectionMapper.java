package id.co.erdigma.satudata.modules.collection.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import id.co.erdigma.satudata.modules.collection.dto.CollectionResponse;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetCollection;

@Mapper(componentModel = "spring")
public interface CollectionMapper {
    CollectionMapper INSTANCE = Mappers.getMapper(CollectionMapper.class);

    @Mapping(target = "datasets", ignore = true)
    @Mapping(target = "datasetCount", ignore = true)
    CollectionResponse toResponse(DatasetCollection collection);

    List<CollectionResponse> toResponseList(List<DatasetCollection> collections);
}
