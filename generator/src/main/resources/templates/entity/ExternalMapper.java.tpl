package {{externalMapperPackage}};
import {{generatedExternalModelPackage}}.{{generatedExternalDto}};
import {{generatedExternalModelPackage}}.{{generatedExternalSearchCriteria}};
import {{generatedModelPackage}}.{{generatedInternalSearchCriteria}};
import {{generatedModelPackage}}.{{generatedPageResultDto}};
import {{modelPackage}}.{{entity}};
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.tkit.quarkus.jpa.daos.PageResult;
import org.tkit.quarkus.rs.mappers.OffsetDateTimeMapper;
@Mapper(uses = { OffsetDateTimeMapper.class })
public interface {{entity}}Mapper {
    {{generatedExternalDto}} toDto({{entity}} entity);
    {{generatedInternalSearchCriteria}} toCriteria({{generatedExternalSearchCriteria}} criteria);
    @Mapping(target = "removeStreamItem", ignore = true)
    {{generatedPageResultDto}} mapPageResult(PageResult<{{entity}}> pageResult);
}
