package org.tkit.onecx.onecxsvcgen.model;

public record ApiDef(
        boolean expose,
        String parent,
        String field,
        boolean parentFieldCollection,
        String path,
        String tag
) {
}
