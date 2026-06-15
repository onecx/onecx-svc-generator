package {{controllerPackage}};
import {{generatedApiPackage}}.{{generatedApiInterface}};
import {{generatedModelPackage}}.{{generatedDto}};
import {{generatedModelPackage}}.{{entity}}SearchCriteriaDTO;
import {{generatedModelPackage}}.ProblemDetailResponseDTO;
import {{mapperPackage}}.InternalExceptionMapper;
import {{mapperPackage}}.{{entity}}Mapper;
import {{domainServicePackage}}.{{entity}}Service;
import {{daoPackage}}.{{entity}}DAO;
import {{modelPackage}}.{{entity}};
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.tkit.quarkus.jpa.exceptions.ConstraintException;
@ApplicationScoped
@Transactional(Transactional.TxType.NOT_SUPPORTED)
public class {{entity}}Controller implements {{generatedApiInterface}} {

    @Inject
    {{entity}}Service service;

    @Inject
    {{entity}}Mapper mapper;

    @Inject
    InternalExceptionMapper exceptionMapper;

    @Inject
    {{entity}}DAO dao;
    @Override
    public Response create{{entity}}({{generatedDto}} dto) {
        var created = service.create(dto);
        return Response.status(Response.Status.CREATED)
                .entity(mapper.toDto(created))
                .build();
    }
    @Override
    public Response get{{entity}}ById(String id) {
        {{entity}} {{entityField}} = dao.findById(id);
        if ({{entityField}} == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(mapper.toDto({{entityField}})).build();
    }
    @Override
    public Response update{{entity}}(String id, {{generatedDto}} dto) {
        var updated = service.update(id, dto);
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(mapper.toDto(updated)).build();
    }
    @Override
    public Response delete{{entity}}(String id) {
        {{entity}} {{entityField}} = dao.findById(id);
        if ({{entityField}} == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        dao.deleteQueryById(id);
        return Response.noContent().build();
    }
    @Override
    public Response search{{resourceOperationPlural}}({{entity}}SearchCriteriaDTO criteria) {
        var result = dao.findByCriteria(criteria);
        return Response.ok(mapper.toPageResultDto(result)).build();
    }
    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTO> exception(ConstraintException ex) {
        return exceptionMapper.exception(ex);
    }
    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTO> constraint(ConstraintViolationException ex) {
        return exceptionMapper.constraint(ex);
    }
    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTO> daoException(OptimisticLockException ex) {
        return exceptionMapper.optimisticLock(ex);
    }
}
