package {{daoPackage}};

import {{modelPackage}}.{{entity}};
import jakarta.enterprise.context.ApplicationScoped;
import org.tkit.quarkus.jpa.daos.AbstractDAO;

@ApplicationScoped
public class {{entity}}DAO extends AbstractDAO<{{entity}}> {
}