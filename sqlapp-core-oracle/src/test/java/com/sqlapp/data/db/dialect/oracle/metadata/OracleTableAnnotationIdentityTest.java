/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.dialect.oracle.sql.OracleAnnotationUtils;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;

class OracleTableAnnotationIdentityTest {
	private ExResultSet row(String owner, String name, String type, String column) {
		var values = new java.util.HashMap<>(Map.of("ANNOTATION_OWNER", owner, "OBJECT_NAME", name, "OBJECT_TYPE", type,
				"ANNOTATION_NAME", "purpose", "ANNOTATION_VALUE", "target"));
		values.put("COLUMN_NAME", column);
		return new ExResultSet(null) {
			@Override
			public String getString(String key) {
				return values.get(key);
			}
		};
	}

	private List<Table> tables() {
		List<Table> tables = new ArrayList<>();
		for (String owner : List.of("APP", "App")) {
			for (String name : List.of("T", "t")) {
				Table table = new Table(name).setSchemaName(owner);
				table.getColumns().add(new Column("C"));
				table.getColumns().add(new Column("c"));
				table.getIndexes().add(new Index("I"));
				table.getIndexes().add(new Index("i"));
				tables.add(table);
			}
		}
		return tables;
	}

	@Test
	void tablesAndColumnsUseExactOwnerAndName() throws Exception {
		var reader = new Oracle23aiTableReader(DialectHolder.oracle23aiDialect);
		var tables = tables();
		reader.applyAnnotation(row("App", "t", "TABLE", null), tables);
		reader.applyAnnotation(row("App", "t", "TABLE", "c"), tables);
		for (Table table : tables) {
			boolean selected = "App".equals(table.getSchemaName()) && "t".equals(table.getName());
			assertEquals(selected, !OracleAnnotationUtils.getAnnotations(table).isEmpty());
			for (Column column : table.getColumns()) {
				assertEquals(selected && "c".equals(column.getName()),
						!OracleAnnotationUtils.getAnnotations(column).isEmpty());
			}
		}
	}

	@Test
	void indexesUseExactOwnerAndName() throws Exception {
		var reader = new Oracle23aiTableReader(DialectHolder.oracle26aiDialect);
		var tables = tables();
		// Index names are schema-wide. Use distinct names on the second table.
		for (Table table : tables)
			if ("T".equals(table.getName()))
				table.getIndexes().clear();
		reader.applyAnnotation(row("App", "i", "INDEX", null), tables);
		for (Table table : tables)
			for (Index index : table.getIndexes()) {
				assertEquals("App".equals(table.getSchemaName()) && "i".equals(index.getName()),
						!OracleAnnotationUtils.getAnnotations(index).isEmpty());
			}
		Table table = new Table("T").setSchemaName("APP");
		Index other = new Index("OTHER");
		table.getIndexes().add(other);
		reader.applyAnnotation(row("OTHER_SCHEMA", "OTHER", "INDEX", null), List.of(table));
		assertTrue(OracleAnnotationUtils.getAnnotations(other).isEmpty());
		reader.applyAnnotation(row("APP", "OTHER", "INDEX", null), List.of(table));
		assertFalse(OracleAnnotationUtils.getAnnotations(other).isEmpty());
	}
}
