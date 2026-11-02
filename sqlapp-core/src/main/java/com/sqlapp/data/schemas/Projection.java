/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import java.util.function.Supplier;
import javax.xml.stream.XMLStreamException;
import com.sqlapp.data.schemas.properties.TableNameProperty;
import com.sqlapp.data.schemas.properties.TableSchemaNameProperty;
import com.sqlapp.data.schemas.properties.object.ReferenceColumnsProperty;
import com.sqlapp.data.schemas.properties.object.SortColumnsProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/** A schema-owned physical representation of a source table, not a logical view.
 * SELECT text belongs in statement; complete executable vendor DDL in definition.
 * Source tables are in the projection's catalog. Vendor placement/redundancy
 * design belongs in specifics; node-local runtime state is supplemental metadata.
 */
public final class Projection extends AbstractSchemaObject<Projection> implements
        HasParent<ProjectionCollection>, TableNameProperty<Projection>, TableSchemaNameProperty<Projection>,
        ReferenceColumnsProperty<Projection>, SortColumnsProperty<Projection> {
    private static final long serialVersionUID = 1L;
    private String tableName;
    private String tableSchemaName;
    private ReferenceColumnCollection columns = new ReferenceColumnCollection(this);
    private ReferenceColumnCollection sortColumns = new ReferenceColumnCollection(this);
    public Projection() { }
    public Projection(String name) { super(name); }
    @Override protected Supplier<Projection> newInstance() { return Projection::new; }
    @Override public ProjectionCollection getParent() { return (ProjectionCollection) super.getParent(); }
    @Override public String getTableName() { return tableName; }
    @Override public Projection setTableName(String value) { tableName = value; return this; }
    @Override public String getTableSchemaName() { return tableSchemaName; }
    @Override public Projection setTableSchemaName(String value) { tableSchemaName = value; return this; }
    @Override public ReferenceColumnCollection getColumns() { return columns; }
    protected Projection setColumns(ReferenceColumnCollection value) { columns = value; columns.setParent(this); return this; }
    @Override public ReferenceColumnCollection getSortColumns() { return sortColumns; }
    protected Projection setSortColumns(ReferenceColumnCollection value) { sortColumns = value; sortColumns.setParent(this); return this; }

    /** Resolves within the owning catalog, without guessing across schemas. */
    public Table resolveSourceTable() {
        if (tableName == null) return null;
        Catalog catalog = getAncestor(Catalog.class);
        if (catalog == null) {
            Schema schema = getSchema();
            if (schema == null || tableSchemaName != null && !tableSchemaName.equals(schema.getName())) return null;
            return schema.getTables().get(tableName);
        }
        Table found = null;
        for (Schema schema : catalog.getSchemas()) {
            if (tableSchemaName != null && !tableSchemaName.equals(schema.getName())) continue;
            Table candidate = schema.getTables().get(tableName);
            if (candidate != null) {
                if (found != null) throw new IllegalArgumentException("Ambiguous projection source table: " + tableName + "; specify tableSchemaName");
                found = candidate;
            }
        }
        return found;
    }
    @Override public boolean equals(Object obj, EqualsHandler handler) {
        if (!(obj instanceof Projection) || !super.equals(obj, handler)) return false;
        Projection other = (Projection) obj;
        if (!equals(SchemaProperties.TABLE_NAME, other, handler)) return false;
        if (!equals(SchemaProperties.TABLE_SCHEMA_NAME, other, handler)) return false;
        if (!equals(SchemaObjectProperties.REFERENCE_COLUMNS, other, handler)) return false;
        if (!equals(SchemaObjectProperties.SORT_COLUMNS, other, handler)) return false;
        return handler.equalsResult(this, obj);
    }
    @Override protected void writeXmlOptionalAttributes(StaxWriter writer) throws XMLStreamException {
        super.writeXmlOptionalAttributes(writer);
        writer.writeAttribute(SchemaProperties.TABLE_SCHEMA_NAME.getLabel(), tableSchemaName);
        writer.writeAttribute(SchemaProperties.TABLE_NAME.getLabel(), tableName);
    }
    @Override protected void writeXmlOptionalValues(StaxWriter writer) throws XMLStreamException {
        columns.writeXml("columns", writer);
        sortColumns.writeXml("sortColumns", writer);
        super.writeXmlOptionalValues(writer);
    }
    @Override protected void toStringDetail(ToStringBuilder builder) {
        builder.add(SchemaProperties.TABLE_SCHEMA_NAME, tableSchemaName);
        builder.add(SchemaProperties.TABLE_NAME, tableName);
        builder.add(SchemaObjectProperties.REFERENCE_COLUMNS, columns);
        builder.add(SchemaObjectProperties.SORT_COLUMNS, sortColumns);
    }
}
