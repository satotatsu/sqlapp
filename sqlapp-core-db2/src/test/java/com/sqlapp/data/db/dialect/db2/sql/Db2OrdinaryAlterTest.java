/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class Db2OrdinaryAlterTest {
	private Table table() {
		var table = new Table("T\".Q").setSchemaName("APP");
		table.getColumns().add("V", c -> c.setDataType(DataType.INT));
		return table;
	}

	private List<SqlOperation> plan(Table before, Table after) {
		SqlFactory<Table> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(after,
				SqlType.ALTER);
		return factory.createDiffSql(before.diff(after));
	}

	private List<String> sql(Table before, Table after) {
		return plan(before, after).stream().filter(o -> o.getSqlType() != SqlType.COMMENT).map(SqlOperation::getSqlText)
				.toList();
	}

	@Test
	void defaultAddChangeAndDropUseNativeClausesWithoutRedefiningType() {
		var before = table();
		var after = before.clone();
		after.getColumns().get("V").setDefaultValue("1");
		assertEquals(List.of("ALTER TABLE \"APP\".\"T\"\".Q\" ALTER COLUMN \"V\" SET DEFAULT 1"), sql(before, after));
		var changed = after.clone();
		changed.getColumns().get("V").setDefaultValue("2");
		assertTrue(sql(after, changed).getFirst().endsWith("SET DEFAULT 2"));
		assertTrue(sql(after, before).getFirst().endsWith("DROP DEFAULT"));
		assertEquals(table(), before);
	}

	@Test
	void nullableAndWideningChangesRetainMaintenanceInformationWithoutBackfill() {
		var before = table();
		var after = before.clone();
		after.getColumns().get("V").setNotNull(true);
		assertTrue(sql(before, after).getFirst().endsWith("SET NOT NULL"));
		assertTrue(sql(after, before).getFirst().endsWith("DROP NOT NULL"));
		assertTrue(plan(before, after).stream()
				.anyMatch(o -> o.getSqlType() == SqlType.COMMENT && o.getSqlText().contains("REORG")));
		after = before.clone();
		after.getColumns().get("V").setDataType(DataType.BIGINT);
		assertTrue(sql(before, after).getFirst().endsWith("SET DATA TYPE BIGINT"));
		before.getColumns().get("V").setDataType(DataType.VARCHAR).setLength(10L).setStringUnits("OCTETS");
		after = before.clone();
		after.getColumns().get("V").setLength(20L);
		assertTrue(sql(before, after).getFirst().endsWith("SET DATA TYPE VARCHAR(20 OCTETS)"));
		assertTrue(plan(before, after).stream().noneMatch(o -> o.getSqlType() == SqlType.UPDATE_TABLE));
	}

	@Test
	void commentsChangeAndClearWithoutColumnAlterAndPreserveQuotes() {
		var before = table();
		var after = before.clone();
		after.setRemarks("table's note");
		after.getColumns().get("V").setRemarks("column's note");
		var statements = sql(before, after);
		assertEquals(2, statements.size());
		assertTrue(statements.stream()
				.anyMatch(value -> value.equals("COMMENT ON TABLE \"APP\".\"T\"\".Q\" IS 'table''s note'")));
		assertTrue(statements.stream()
				.anyMatch(value -> value.equals("COMMENT ON COLUMN \"APP\".\"T\"\".Q\".\"V\" IS 'column''s note'")));
		assertTrue(sql(after, before).stream().allMatch(value -> value.endsWith("IS ''")));
	}

	@Test
	void schemaAndCatalogDiffsComposeTheSameNativeChanges() {
		var before = new Catalog();
		var schema = new Schema("APP");
		schema.getTables().add(table());
		before.getSchemas().add(schema);
		var after = before.clone();
		var changed = after.getSchemas().get("APP").getTables().getFirst();
		changed.getColumns().get("V").setDefaultValue("3");
		changed.setRemarks("changed");
		SqlFactory<Catalog> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(after,
				SqlType.ALTER);
		var statements = factory.createDiffSql(before.diff(after)).stream().map(SqlOperation::getSqlText).toList();
		assertInstanceOf(Db2AlterTableFactory.class,
				factory.getSqlFactoryRegistry().getSqlFactory(changed, State.Modified));
		assertInstanceOf(com.sqlapp.data.db.dialect.db2.Db2_1110.class,
				((AbstractSqlFactory<?, ?>) factory).getDialect());
		assertTrue(statements.stream().anyMatch(value -> value.endsWith("SET DEFAULT 3")), statements.toString());
		assertTrue(statements.stream().anyMatch(value -> value.startsWith("COMMENT ON TABLE")), statements.toString());
		assertNull(before.getSchemas().get("APP").getTables().getFirst().getColumns().get("V").getDefaultValue());
	}

	@Test
	void unsupportedAttributesConversionsAndNarrowingFailInsteadOfReturningPartialPlans() {
		var before = table();
		var after = before.clone();
		after.getColumns().get("V").setFormula("1");
		assertThrows(IllegalArgumentException.class, () -> plan(before, after));
		var narrow = before.clone();
		narrow.getColumns().get("V").setDataType(DataType.SMALLINT);
		assertThrows(IllegalArgumentException.class, () -> plan(before, narrow));
		var identity = before.clone();
		identity.getColumns().get("V").setIdentity(true);
		var identityTarget = identity.clone();
		identityTarget.getColumns().get("V").setDefaultValue("1");
		assertThrows(IllegalArgumentException.class, () -> plan(identity, identityTarget));
		var special = before.clone();
		special.setRemarks("valid part");
		special.getColumns().get("V").setOnUpdate("CURRENT TIMESTAMP");
		assertThrows(IllegalArgumentException.class, () -> plan(before, special));
	}

	@Test
	void decimalWideningRenameAndLegacyVersionBoundaryAreExplicit() {
		var before = table();
		before.getColumns().get("V").setDataType(DataType.DECIMAL).setLength(10L).setScale(2);
		var after = before.clone();
		after.getColumns().get("V").setLength(18L);
		assertTrue(sql(before, after).getFirst().endsWith("SET DATA TYPE DECIMAL(18,2)"));
		after.getColumns().get("V").setScale(3);
		assertThrows(IllegalArgumentException.class, () -> plan(before, after));
		var renamed = before.clone();
		renamed.getColumns().get("V").setName("NEW\".Q");
		assertTrue(sql(before, renamed).getFirst().endsWith("RENAME COLUMN \"V\" TO \"NEW\"\".Q\""));
		assertFalse(DialectHolder.Db2_1050Dialect.createSqlFactoryRegistry().getSqlFactory(new Function("F"),
				SqlType.ALTER) instanceof Db2AlterFunctionFactory);
	}

	@Test
	void simpleAddedAndDeletedColumnsPreserveCommentsAndQuotedNames() {
		var before = table();
		var after = before.clone();
		after.getColumns().add("NEW\".Q", c -> c.setDataType(DataType.INT).setRemarks("new"));
		assertEquals(2, sql(before, after).size());
		assertTrue(sql(before, after).getFirst().contains("ADD COLUMN \"NEW\"\".Q\" INTEGER"));
		assertTrue(sql(after, before).getFirst().endsWith("DROP COLUMN \"NEW\"\".Q\""));
	}
}
