/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.data.schemas.Table;

class SpannerSynonymFactoryTest {
	@Test
	void replacesTheXmlTargetThroughAutomaticDifferenceRouting() throws Exception {
		final var original = new Synonym("Select").setSchemaName("Sales").setObjectName("Orders")
				.setDialect(DialectHolder.defaultDialect);
		final Synonym target = SchemaUtils.readXml(new StringReader(original.clone().setObjectName("Archive").asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var operations = registry.createSql(original.diff(target));
		assertEquals(2, operations.size());
		assertEquals(SqlType.DROP, operations.getFirst().getSqlType());
		assertEquals(SqlType.CREATE, operations.getLast().getSqlType());
		assertEquals("ALTER TABLE `Sales`.`Orders` DROP SYNONYM `Sales`.`Select`",
				operations.getFirst().getSqlText());
		assertEquals("ALTER TABLE `Sales`.`Archive` ADD SYNONYM `Sales`.`Select`",
				operations.getLast().getSqlText());
		assertEquals("Orders", original.getObjectName());
		assertEquals("Archive", target.getObjectName());
		final var renamed = original.clone().setName("Legacy");
		assertEquals("ALTER TABLE `Sales`.`Orders` ADD SYNONYM `Sales`.`Legacy`",
				registry.createSql(original.diff(renamed)).getLast().getSqlText());
		assertTrue(registry.createSql(original.diff(original)).isEmpty());
		assertTrue(registry.createSql(original.diff(original.clone().setObjectSchemaName("Sales"))).isEmpty());
	}

	@Test
	void routesTargetChangesFromTheContainingSchema() {
		final var original = new Schema("Sales").setDialect(DialectHolder.defaultDialect);
		original.getSynonyms().add(new Synonym("Legacy").setObjectName("Orders"));
		final var target = original.clone();
		target.getSynonyms().getFirst().setObjectName("ArchivedOrders");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var operations = registry.createSql(original.diff(target));
		assertEquals(2, operations.size());
		assertEquals("ALTER TABLE `Sales`.`Orders` DROP SYNONYM `Sales`.`Legacy`",
				operations.getFirst().getSqlText());
		assertEquals("ALTER TABLE `Sales`.`ArchivedOrders` ADD SYNONYM `Sales`.`Legacy`",
				operations.getLast().getSqlText());
	}

	@Test
	void rejectsInvalidOrUnrepresentedSynonymChangesBeforeReturningSql() {
		final var original = new Synonym("Legacy").setSchemaName("Sales").setObjectName("Orders");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(
				original.diff(original.clone().setObjectName(" ")), SqlType.ALTER));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(
				original.diff(original.clone().setObjectSchemaName("Archive")), SqlType.ALTER));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(
				original.diff(original.clone().setDbLinkName("remote")), SqlType.ALTER));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(
				original.diff(original.clone().setSchemaName("Archive")), SqlType.ALTER));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original, SqlType.ALTER));
	}

	@Test
	void addsTheXmlSynonymAfterItsTableAndDropsItThroughTheSameTable() throws Exception {
		final var schema = new Schema("Sales");
		final var table = new Table("Orders");
		table.getColumns().add(new Column("id").setDataType(DataType.BIGINT));
		schema.getTables().add(table);
		schema.getSynonyms().add(new Synonym("Select").setObjectName("Orders"));
		final Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var operations = registry.createSql(restored, SqlType.CREATE);
		assertEquals(3, operations.size());
		assertTrue(operations.get(1).getSqlText().contains("`Sales`.`Orders`"));
		assertEquals("ALTER TABLE `Sales`.`Orders` ADD SYNONYM `Sales`.`Select`",
				operations.getLast().getSqlText());
		assertEquals("ALTER TABLE `Sales`.`Orders` DROP SYNONYM `Sales`.`Select`",
				registry.createSql(restored.getSynonyms().getFirst(), SqlType.DROP).getFirst().getSqlText());
	}

	@Test
	void rejectsMissingTargetsDatabaseLinksAndCrossSchemaTargets() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var synonym = new Synonym("Legacy").setSchemaName("Sales");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(synonym, SqlType.CREATE));
		synonym.setObjectName("Orders").setObjectSchemaName("Archive");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(synonym, SqlType.DROP));
		synonym.setObjectSchemaName("Sales").setDbLinkName("remote");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(synonym, SqlType.CREATE));
	}
}
