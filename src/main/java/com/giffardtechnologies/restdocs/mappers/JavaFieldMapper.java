package com.giffardtechnologies.restdocs.mappers;

import com.giffardtechnologies.restdocs.JavaGenerator;
import com.giffardtechnologies.restdocs.domain.type.Field;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

@Mapper()
public interface JavaFieldMapper {
    JavaFieldMapper INSTANCE = Mappers.getMapper(JavaFieldMapper.class);

    @Mappings({@Mapping(target = "typeName", ignore = true)})
    JavaGenerator.JavaField dtoToJavaModel(Field field);

    @InheritConfiguration
    void updateJavaModel(Field fieldO, @MappingTarget JavaGenerator.JavaField jvaField);

}
