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

    @BeanMapping(ignoreByDefault = true)
    {{generatedDto}} toDto({{entity}} entity);

    @BeanMapping(ignoreByDefault = true)
    {{generatedPageResultDto}} toPageResultDto(PageResult<{{entity}}> page);

    @BeanMapping(ignoreByDefault = true)
    {{entity}} fromDto({{generatedDto}} dto);

    @BeanMapping(ignoreByDefault = true)
    void update({{generatedDto}} dto, @MappingTarget {{entity}} entity);

{{relationMappingMethods}}
}