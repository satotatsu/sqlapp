package com.sqlapp.data.db.metadata;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.*;
/** Optional bulk physical-projection metadata extension. */
public abstract class ProjectionReader extends AbstractSchemaObjectReader<Projection> {
    protected ProjectionReader(Dialect dialect) { super(dialect); }
    @Override protected SchemaObjectProperties getSchemaObjectProperties() { return SchemaObjectProperties.PROJECTIONS; }
    @Override protected String getNameLabel() { return DbObjects.PROJECTION.getCamelCaseNameLabel(); }
}
