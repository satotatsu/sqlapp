package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProjectionTest {
    private Catalog catalog() {
        Catalog catalog = new Catalog("db");
        Schema source = new Schema("source");
        source.getTables().add(new Table("events"));
        catalog.getSchemas().add(source);
        Schema physical = new Schema("physical");
        catalog.getSchemas().add(physical);
        Projection p = new Projection("events_projection");
        p.setTableSchemaName("source").setTableName("events");
        p.setStatement("SELECT id, value FROM source.events");
        p.setDefinition("CREATE PROJECTION physical.events_projection AS SELECT id, value FROM source.events ORDER BY id UNSEGMENTED ALL NODES");
        p.getColumns().add("id");
        p.getColumns().add("value");
        p.getSortColumns().add("id");
        p.getSortColumns().get(0).setOrder(Order.Desc);
        p.getSpecifics().put("KSAFE", "1");
        physical.getProjections().add(p);
        return catalog;
    }
    @Test void catalogXmlCloneAndDifferenceIncludeProjection() throws Exception {
        Catalog original = catalog();
        Catalog restored = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
        Projection p = restored.getSchemas().get("physical").getProjections().get(0);
        assertEquals(original, restored);
        assertSame(restored.getSchemas().get("physical").getProjections(), p.getParent());
        assertEquals("physical", p.getSchemaName());
        assertSame(restored.getSchemas().get("source").getTables().get("events"), p.resolveSourceTable());
        assertEquals(2, p.getColumns().size());
        assertEquals(Order.Desc, p.getSortColumns().get(0).getOrder());
        Catalog copy = restored.clone();
        assertEquals(restored, copy);
        Projection cloned = copy.getSchemas().get("physical").getProjections().get(0);
        assertNotSame(p, cloned);
        cloned.setStatement("SELECT id FROM source.events");
        assertNotEquals(restored, copy);
        assertFalse(restored.diff(copy).getProperties().isEmpty());
        assertEquals(2, p.getColumns().size());
        cloned.getColumns().add("clone_only");
        assertEquals(2, p.getColumns().size());
        Projection standalone = SchemaUtils.readXml(new java.io.StringReader(p.asXml()));
        assertEquals(p.getDefinition(), standalone.getDefinition());
    }
    @Test void createRequiresCompleteDefinitionAndSchemaOrdersItAfterTables() {
        var registry = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDefaultDialect().createSqlFactoryRegistry();
        Projection projection = new Projection("p");
        projection.setStatement("SELECT 1");
        assertThrows(IllegalArgumentException.class, () -> registry.createSql(projection, com.sqlapp.data.db.sql.SqlType.CREATE));
        projection.setDefinition("CREATE PROJECTION p AS SELECT id FROM t UNSEGMENTED ALL NODES");
        assertEquals(projection.getDefinition().get(0), registry.createSql(projection, com.sqlapp.data.db.sql.SqlType.CREATE).get(0).getSqlText());
        Schema schema = new Schema("s");
        Table table = new Table("t");
        table.getColumns().add("id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT));
        schema.getTables().add(table);
        schema.getProjections().add(projection);
        var operations = registry.createSql(schema, com.sqlapp.data.db.sql.SqlType.CREATE);
        int tablePosition = -1;
        int projectionPosition = -1;
        for (int i = 0; i < operations.size(); i++) {
            if (operations.get(i).getSqlText().contains("CREATE TABLE")) tablePosition = i;
            if (operations.get(i).getSqlText().contains("CREATE PROJECTION")) projectionPosition = i;
        }
        assertTrue(tablePosition >= 0 && projectionPosition > tablePosition);
        Catalog catalog = new Catalog("db");
        catalog.getSchemas().add(schema);
        Schema source = new Schema("later");
        source.getTables().add(new Table("t"));
        catalog.getSchemas().add(source);
        projection.setTableSchemaName("later").setTableName("t");
        operations = registry.createSql(catalog, com.sqlapp.data.db.sql.SqlType.CREATE);
        int lastTable = -1;
        int firstProjection = -1;
        for (int i = 0; i < operations.size(); i++) {
            if (operations.get(i).getOriginal() instanceof Table) lastTable = i;
            if (operations.get(i).getOriginal() instanceof Projection && firstProjection < 0) firstProjection = i;
        }
        assertTrue(firstProjection > lastTable && lastTable >= 0);
    }
    @Test void shortSourceNamesRequireUniqueResolutionAndMissingSourcesRemainMissing() {
        Catalog catalog = catalog();
        Projection p = catalog.getSchemas().get("physical").getProjections().get(0);
        p.setTableSchemaName(null);
        assertNotNull(p.resolveSourceTable());
        catalog.getSchemas().get("physical").getTables().add(new Table("events"));
        assertThrows(IllegalArgumentException.class, p::resolveSourceTable);
        p.setTableSchemaName("missing");
        assertNull(p.resolveSourceTable());
    }
}
