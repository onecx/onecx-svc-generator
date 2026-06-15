package {{daoPackage}};

import {{generatedModelPackage}}.{{entity}}SearchCriteriaDTO;
import {{modelPackage}}.{{entity}};
import {{modelPackage}}.{{entity}}_;
import static org.tkit.quarkus.jpa.utils.QueryCriteriaUtil.addSearchStringPredicate;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Predicate;

import org.tkit.quarkus.jpa.daos.AbstractDAO;
import org.tkit.quarkus.jpa.daos.Page;
import org.tkit.quarkus.jpa.daos.PageResult;
import org.tkit.quarkus.jpa.models.AbstractTraceableEntity_;

@ApplicationScoped
public class {{entity}}DAO extends AbstractDAO<{{entity}}> {

    public PageResult<{{entity}}> findByCriteria({{entity}}SearchCriteriaDTO criteria) {
        try {
            var cb = getEntityManager().getCriteriaBuilder();
            var cq = cb.createQuery({{entity}}.class);
            var root = cq.from({{entity}}.class);

            List<Predicate> predicates = new ArrayList<>();

            {{findByCriteriaPredicates}}

            if (!predicates.isEmpty()) {
                cq.where(cb.and(predicates.toArray(new Predicate[0])));
            }
            cq.orderBy(cb.desc(root.get(AbstractTraceableEntity_.CREATION_DATE)));

            return createPageQuery(cq, Page.of(criteria.getPageNumber(), criteria.getPageSize())).getPageResult();
        } catch (Exception ex) {
            throw handleConstraint(ex, ErrorKeys.ERROR_FIND_BY_CRITERIA);
        }
    }

    public enum ErrorKeys {
        ERROR_FIND_BY_CRITERIA
    }
}