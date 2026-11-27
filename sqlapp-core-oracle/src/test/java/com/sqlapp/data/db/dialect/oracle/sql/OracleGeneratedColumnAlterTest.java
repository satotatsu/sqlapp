/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;

class OracleGeneratedColumnAlterTest {
	private Table table() {
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("V").setDataType(DataType.INT).setFormula("ID*2"));
		return table;
	}

	private java.util.List<String> diff(Table before, Table after) {
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		return factory.createDiffSql(before.diff(after)).stream().map(op -> op.getSqlText().replaceAll("\\s+", " "))
				.toList();
	}

	@Test
	void formulaChangesUseModifyWithoutDroppingTheColumn() throws Exception {
		Table before = table(), after = before.clone();
		after.getColumns().get("V").setFormula("ID*3");
		Table restored = SchemaUtils.readXml(new java.io.StringReader(after.asXml()));
		var sql = diff(before, restored);
		assertEquals(1, sql.size());
		assertTrue(sql.get(0).contains("GENERATED ALWAYS AS (ID*3) VIRTUAL"), sql.toString());
		assertFalse(sql.get(0).contains("DROP"));
		assertFalse(sql.get(0).contains("DEFAULT"));
	}

	@Test
	void dependenciesAndCombinedChangesAreRejected() {
		Table before = table(), after = before.clone();
		after.getColumns().get("V").setFormula("ID*3").setDataType(DataType.BIGINT);
		assertThrows(IllegalArgumentException.class, () -> diff(before, after));
		after.getColumns().get("V").setDataType(DataType.INT);
		Index index = new Index("IX");
		index.getColumns().add("V");
		before.getIndexes().add(index);
		assertThrows(IllegalArgumentException.class, () -> diff(before, after));
	}

	@Test
	void generatedColumnAndTableCommentsDoNotEmitModifyOrDuplicateIs() {
		for (String kind : java.util.List.of("plain", "virtual", "identity")) {
			Table before = table();
			if (kind.equals("plain"))
				before.getColumns().get("V").setFormula(null);
			if (kind.equals("identity"))
				before.getColumns().get("V").setFormula(null).setIdentity(true)
						.setIdentityGenerationType(IdentityGenerationType.Always);
			Table after = before.clone();
			after.setRemarks("table's comment");
			after.getColumns().get("V").setRemarks("column's comment");
			var sql = diff(before, after);
			assertEquals(2, sql.size(), sql.toString());
			assertTrue(sql.stream().allMatch(s -> s.startsWith("COMMENT ON")), sql.toString());
			assertTrue(sql.stream().anyMatch(s -> s.contains("'column''s comment'")));
			var clear = diff(after, before);
			assertEquals(2, clear.size(), clear.toString());
			assertTrue(clear.stream().allMatch(s -> s.endsWith("IS NULL")), clear.toString());
			assertTrue(clear.stream().noneMatch(s -> s.contains("IS IS")));
		}
	}
}
