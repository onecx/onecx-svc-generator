package {{externalMapperPackage}};
import {{generatedExternalModelPackage}}.{{generatedExternalDto}};
import {{generatedExternalModelPackage}}.{{generatedExternalSearchCriteria}};
import {{generatedModelPackage}}.{{generatedInternalSearchCriteria}};
{{externalMapperPageResultImports}}import {{modelPackage}}.{{entity}};
import org.mapstruct.Mapper;
{{externalMapperMappingImport}}import org.tkit.quarkus.rs.mappers.OffsetDateTimeMapper;
@Mapper(uses = { OffsetDateTimeMapper.class })
public interface {{entity}}Mapper {

    {{generatedExternalDto}} toDto({{entity}} entity);

    {{generatedInternalSearchCriteria}} toCriteria({{generatedExternalSearchCriteria}} criteria);{{mapPageResultMethod}}
}
