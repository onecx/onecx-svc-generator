package {{modelPackage}};

{{entityImports}}import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.tkit.quarkus.jpa.models.TraceableEntity;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "{{tableName}}")
@Getter
@Setter
public class {{entity}} extends TraceableEntity {

    @TenantId
    @Column(name = "TENANT_ID")
    private String tenantId;

{{fieldsDecl}}{{relationsDecl}}
}