/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleCatalogDomainOrderTest {
	@Test
	void crossSchemaDomainsAndPrerequisitesPrecedeTablesAfterXml() throws Exception {
		Catalog catalog = new Catalog("CAT");
		Schema first = new Schema("FIRST");
		Schema second = new Schema("SECOND");
		catalog.getSchemas().add(first);
		catalog.getSchemas().add(second);
		Domain dependent = new Domain("D").setDefinition(
				"CREATE FLEXIBLE DOMAIN FIRST.D (v) CHOOSE DOMAIN USING (kind NUMBER) FROM DECODE(kind, 1, SECOND.D(v))");
		dependent.getSpecifics().put(OracleDomainDependencies.DEPENDENCIES, "[[\"SECOND\",\"D\"]]");
		first.getDomains().add(dependent);
		Domain base = new Domain("D").setDataType(DataType.DECIMAL);
		second.getDomains().add(base);
		second.getSequences().add(new Sequence("SEQ"));
		Table table = new Table("T");
		first.getTables().add(table);
		table.getColumns().add(new Column("V").setDataType(DataType.DECIMAL).setDomain(base));
		Catalog restored = SchemaUtils.readXml(new java.io.StringReader(catalog.asXml()));
		for (var dialect : List.of(DialectHolder.oracle23aiDialect, DialectHolder.oracle26aiDialect)) {
			var operations = dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE);
			var objects = operations.stream().map(o -> o.getOriginal()).toList();
			var seq = restored.getSchemas().get("SECOND").getSequences().get("SEQ");
			var d = restored.getSchemas().get("SECOND").getDomains().get("D");
			var f = restored.getSchemas().get("FIRST").getDomains().get("D");
			var t = restored.getSchemas().get("FIRST").getTables().get("T");
			assertTrue(objects.indexOf(seq) < objects.indexOf(d));
			assertTrue(objects.indexOf(d) < objects.indexOf(f));
			assertTrue(objects.indexOf(f) < objects.indexOf(t));
			assertEquals(1, objects.stream().filter(d::equals).count());
			assertEquals(List.of("FIRST", "SECOND"), restored.getSchemas().stream().map(Schema::getName).toList());
		}
		assertFalse(DialectHolder.oracle21cDialect.createSqlFactoryRegistry().getSqlFactory(catalog,
				SqlType.CREATE) instanceof Oracle23aiCreateCatalogFactory);
	}

	@Test
	void catalogWithoutDomainsRetainsSchemaOrder() {
		Catalog catalog = new Catalog("CAT");
		Schema first = new Schema("FIRST");
		catalog.getSchemas().add(first);
		Table table = new Table("T");
		first.getTables().add(table);
		table.getColumns().add(new Column("V").setDataType(DataType.INT));
		Schema second = new Schema("SECOND");
		catalog.getSchemas().add(second);
		Sequence sequence = new Sequence("SEQ");
		second.getSequences().add(sequence);
		var objects = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(catalog, SqlType.CREATE)
				.stream().map(o -> o.getOriginal()).toList();
		assertTrue(objects.indexOf(table) < objects.indexOf(sequence));
	}

	@Test
	void rejectsCrossSchemaDomainCycle() {
		Catalog catalog = new Catalog("CAT");
		for (String name : List.of("A", "B")) {
			Schema schema = new Schema(name);
			catalog.getSchemas().add(schema);
			Domain domain = new Domain("D").setDataType(DataType.INT);
			schema.getDomains().add(domain);
			domain.getSpecifics().put(OracleDomainDependencies.DEPENDENCIES,
					"[[\"" + (name.equals("A") ? "B" : "A") + "\",\"D\"]]");
		}
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(catalog, SqlType.CREATE));
	}
}
