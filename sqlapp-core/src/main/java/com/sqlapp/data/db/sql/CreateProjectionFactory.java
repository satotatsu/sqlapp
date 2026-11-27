package com.sqlapp.data.db.sql;
import com.sqlapp.data.schemas.Projection;
import com.sqlapp.util.AbstractSqlBuilder;
/** Complete-definition path; no portable physical-projection syntax is assumed. */
public class CreateProjectionFactory extends AbstractCreateNamedObjectFactory<Projection, AbstractSqlBuilder<?>> {
    @Override protected void addCreateObject(Projection projection, AbstractSqlBuilder<?> builder) {
        if (projection.getDefinition() == null || projection.getDefinition().isEmpty()
                || projection.getDefinition().stream().allMatch(s -> s == null || s.isBlank()))
            throw new IllegalArgumentException("Projection CREATE requires complete executable definition: " + projection.getName());
        builder._add(projection.getDefinition());
    }
}
