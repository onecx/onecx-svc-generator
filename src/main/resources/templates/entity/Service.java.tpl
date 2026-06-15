package {{domainServicePackage}};
import {{daoPackage}}.{{entity}}DAO;
{{serviceRelationImports}}import {{generatedModelPackage}}.{{generatedDto}};
import {{mapperPackage}}.{{entity}}Mapper;
import {{modelPackage}}.{{entity}};
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
@ApplicationScoped
public class {{entity}}Service {

    @Inject
    {{entity}}DAO dao;

    @Inject
    {{entity}}Mapper mapper;
    
{{relationDaoInjections}}
    public {{entity}} create({{generatedDto}} dto) {
        {{entity}} entity = mapper.fromDto(dto);
{{relationCreateResolvers}}        dao.create(entity);
        return entity;
    }
    public {{entity}} update(String id, {{generatedDto}} dto) {
        {{entity}} entity = dao.findById(id);
        if (entity == null) {
            return null;
        }
        mapper.update(dto, entity);
{{relationUpdateResolvers}}        return dao.update(entity);
    }
}
