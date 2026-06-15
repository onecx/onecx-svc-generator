package org.tkit.onecx.onecxsvcgen.model;

import java.util.List;

public record EntityDef(
        String name,
        boolean aggregateRoot,
        ApiDef api,
        List<FieldDef> fields,
        List<RelationDef> relations
) {
}
