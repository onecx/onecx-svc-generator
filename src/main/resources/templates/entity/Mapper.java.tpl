package {{mapperPackage}};

import {{generatedModelPackage}}.{{generatedDto}};
import {{generatedModelPackage}}.{{generatedPageResultDto}};
import {{modelPackage}}.{{entity}};
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.tkit.quarkus.jpa.daos.PageResult;
import org.tkit.quarkus.rs.mappers.OffsetDateTimeMapper;

import java.util.stream.Collectors;

@Mapper(uses = { OffsetDateTimeMapper.class })
public interface {{entity}}Mapper {

    {{generatedDto}} toDto({{entity}} entity);

    @Mapping(target = "removeStreamItem", ignore = true)
    {{generatedPageResultDto}} toPageResultDto(PageResult<{{entity}}> page);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "creationUser", ignore = true)
    @Mapping(target = "modificationDate", ignore = true)
    @Mapping(target = "modificationUser", ignore = true)
    @Mapping(target = "controlTraceabilityManual", ignore = true)
    @Mapping(target = "modificationCount", ignore = true)
    @Mapping(target = "persisted", ignore = true)
    {{entity}} fromDto({{generatedDto}} dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "creationUser", ignore = true)
    @Mapping(target = "modificationDate", ignore = true)
    @Mapping(target = "modificationUser", ignore = true)
    @Mapping(target = "controlTraceabilityManual", ignore = true)
    @Mapping(target = "modificationCount", ignore = true)
    @Mapping(target = "persisted", ignore = true)
    void update({{generatedDto}} dto, @MappingTarget {{entity}} entity);

{{relationMappingMethods}}
}