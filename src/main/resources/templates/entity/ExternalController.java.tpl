package {{externalControllerPackage}};
import {{generatedExternalApiPackage}}.{{generatedExternalApiInterface}};
import {{generatedExternalModelPackage}}.{{generatedExternalDto}};
import {{generatedExternalModelPackage}}.{{generatedExternalSearchCriteria}};
import {{generatedExternalModelPackage}}.ProblemDetailResponseDTOV1;
import {{externalMapperPackage}}.{{entity}}Mapper;
import {{externalMapperPackage}}.ExternalExceptionMapper;
import {{domainServicePackage}}.{{entity}}Service;
import {{daoPackage}}.{{entity}}DAO;
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
public class {{entity}}Controller implements {{generatedExternalApiInterface}} {

    @Inject
    {{entity}}Service service;

    @Inject
    {{entity}}Mapper mapper;

    @Inject
    ExternalExceptionMapper exceptionMapper;

    @Inject
    {{entity}}DAO dao;
    @Override
    public Response get{{entity}}ById{{externalOperationSuffix}}(String id) {
        return Response.ok(mapper.toDto(dao.findById(id))).build();
    }
    @Override
    public Response search{{resourceOperationPlural}}{{externalOperationSuffix}}({{generatedExternalSearchCriteria}} criteria) {
        var pageResult = dao.findByCriteria(mapper.toCriteria(criteria));
        return Response.ok(mapper.mapPageResult(pageResult)).build();
    }
    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTOV1> exception(ConstraintException ex) {
        return exceptionMapper.exception(ex);
    }
    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTOV1> constraint(ConstraintViolationException ex) {
        return exceptionMapper.constraint(ex);
    }
    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTOV1> daoException(OptimisticLockException ex) {
        return exceptionMapper.optimisticLock(ex);
    }
}
