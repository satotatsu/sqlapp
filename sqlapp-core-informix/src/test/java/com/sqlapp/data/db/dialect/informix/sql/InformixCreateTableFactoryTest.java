/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.sqlapp.data.db.dialect.informix.metadata.InformixTableReader.*;

import java.io.StringReader;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.SchemaUtils;

class InformixCreateTableFactoryTest {
	private String sql(final Table table) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).get(0)
				.getSqlText().replaceAll("\\s+", " ").replace("\"", "");
	}

	@Test
	void generatesSerialTypesWithoutIdentityClause() {
		final Table table = new Table("serials");
		table.getColumns().add(new Column("id").setDataType(DataType.INT).setIdentity(true)
				.setIdentityStartValue(BigInteger.valueOf(100)));
		table.getColumns().add(new Column("big_id").setDataType(DataType.BIGSERIAL).setIdentity(true));
		final String sql = sql(table);
		assertTrue(sql.contains("id SERIAL(100)"), sql);
		assertTrue(sql.contains("big_id SERIAL8"), sql);
		assertFalse(sql.contains("IDENTITY"), sql);
		table.getColumns().get(0).setIdentityStep(BigInteger.TWO);
		assertThrows(IllegalArgumentException.class, () -> sql(table));
	}

	@Test
	void preservesExpressionFragmentsThroughXml() throws Exception {
		final Table table = fragmented("E", "(id < 10)");
		final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		final String sql = sql(restored);
		assertTrue(sql.contains("FRAGMENT BY EXPRESSION PARTITION p1 (id < 10) IN rootdbs"), sql);
	}

	@Test
	void generatesListAndIntervalHeaders() {
		final Table table = fragmented("L", "VALUES (1,2)");
		table.getPartitioning().getSpecifics().put(INFORMIX_FRAGMENT_HEADER + "-3", "id");
		assertTrue(sql(table).contains("FRAGMENT BY LIST(id) PARTITION p1 VALUES (1,2) IN rootdbs"));
		table.getPartitioning().getSpecifics().put(INFORMIX_FRAGMENT_STRATEGY, "N");
		table.getPartitioning().getSpecifics().put(INFORMIX_FRAGMENT_HEADER + "-2", "10");
		table.getPartitioning().getSpecifics().put(INFORMIX_FRAGMENT_HEADER + "-1", "rootdbs");
		table.getPartitioning().getPartitions().get(0).getSpecifics().put(INFORMIX_FRAGMENT_EXPRESSION, "VALUES < 10");
		assertTrue(sql(table).contains("FRAGMENT BY RANGE(id) INTERVAL(10) STORE IN(rootdbs)"));
	}

	@Test
	void preservesRollingPolicyAndIntervalDisabledStateThroughXml() throws Exception {
		final Table table = fragmented("N", "VALUES < 10");
		final var specifics = table.getPartitioning().getSpecifics();
		specifics.put(INFORMIX_FRAGMENT_HEADER + "-3", "id");
		specifics.put(INFORMIX_FRAGMENT_HEADER + "-2", "10");
		specifics.put(INFORMIX_FRAGMENT_HEADER + "-1", "rootdbs");
		specifics.put(INFORMIX_ROLLING_FRAGMENTS, "3");
		specifics.put(INFORMIX_ROLLING_SIZE_KB, "102400");
		specifics.put(INFORMIX_ROLLING_ACTION, "DISCARD");
		specifics.put(INFORMIX_ROLLING_SCOPE, "ANY");
		specifics.put(INFORMIX_INTERVAL_DISABLED, "true");
		final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		final var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored,
				SqlType.CREATE);
		assertTrue(operations.get(0).getSqlText().contains("ROLLING(3 FRAGMENTS) LIMIT TO 102400 KB DISCARD ANY"));
		assertTrue(operations.get(1).getSqlText().contains("MODIFY INTERVAL DISABLED"));
		specifics.remove(INFORMIX_ROLLING_ACTION);
		assertThrows(IllegalArgumentException.class, () -> sql(table));
	}

	@Test
	void rejectsDisabledGeneratedFragmentsAndIncompatibleRollingOptions() {
		final Table table = fragmented("N", "VALUES < 10");
		final var specifics = table.getPartitioning().getSpecifics();
		specifics.put(INFORMIX_FRAGMENT_HEADER + "-3", "id");
		specifics.put(INFORMIX_FRAGMENT_HEADER + "-2", "10");
		specifics.put(INFORMIX_FRAGMENT_HEADER + "-1", "rootdbs");
		specifics.put(INFORMIX_INTERVAL_DISABLED, "true");
		table.getPartitioning().getPartitions().get(0).getSpecifics().put(INFORMIX_INTERVAL_FRAGMENT, "true");
		assertThrows(UnsupportedOperationException.class, () -> sql(table));
		table.getPartitioning().getPartitions().get(0).getSpecifics().remove(INFORMIX_INTERVAL_FRAGMENT);
		specifics.put(INFORMIX_ROLLING_FRAGMENTS, "3");
		specifics.put(INFORMIX_ROLLING_ACTION, "DETACH");
		specifics.put(INFORMIX_ROLLING_SCOPE, "ANY");
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		specifics.put(INFORMIX_FRAGMENT_STRATEGY, "E");
		assertThrows(IllegalArgumentException.class, () -> sql(table));
	}

	@Test
	void generatesMixedFunctionalAndQuotedOrdinaryKeysThroughXml() throws Exception {
		final Table table = new Table("function_keys");
		table.getColumns().add("odd (column)", c -> c.setDataType(DataType.INT));
		final var index = new com.sqlapp.data.schemas.Index("ix")
				.setIndexType(com.sqlapp.data.schemas.IndexType.Function);
		index.getColumns().add("\"owner\".\"sum_pair\"(\"id\",\"other\")", c -> {
			c.getSpecifics().put(
					com.sqlapp.data.db.dialect.informix.metadata.InformixIndexReader.INFORMIX_INDEX_EXPRESSION, "true");
			c.setOrder(com.sqlapp.data.schemas.Order.Desc);
		});
		index.getColumns().add(table.getColumns().get(0));
		table.getIndexes().add(index);
		final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE)
				.get(1).getSqlText();
		assertTrue(sql.contains("\"owner\".\"sum_pair\"(\"id\",\"other\") DESC"), sql);
		assertTrue(sql.contains("\"odd (column)\""), sql);
	}

	@Test
	void generatesRoundRobinAndRejectsIncompleteOrUnsupportedFragments() {
		final Table table = fragmented("R", "");
		assertTrue(sql(table).contains("FRAGMENT BY ROUND ROBIN PARTITION p1 IN rootdbs"));
		table.getPartitioning().getSpecifics().put(INFORMIX_FRAGMENT_STRATEGY, "E");
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		table.getPartitioning().getSpecifics().put(INFORMIX_FRAGMENT_STRATEGY, "H");
		assertThrows(UnsupportedOperationException.class, () -> sql(table));
	}

	@Test
	void namesChecksAfterTheirExpressions() {
		final Table table = new Table("checks");
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		table.getConstraints().add(new com.sqlapp.data.schemas.CheckConstraint("ck_id", "id > 0"));
		assertTrue(sql(table).contains("CHECK (id > 0) CONSTRAINT ck_id"));
	}

	@Test
	void generatesClusterIndexKeywordBeforeIndexName() {
		final Table table = new Table("indexed");
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		table.getIndexes().add("ix", table.getColumns().get("id"));
		table.getIndexes().get(0).setIndexType(com.sqlapp.data.schemas.IndexType.Clustered);
		final String indexSql = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE)
				.get(1).getSqlText().replaceAll("\\s+", " ").replace("\"", "");
		assertTrue(indexSql.contains("CREATE CLUSTER INDEX ix ON indexed"), indexSql);
	}

	@Test
	void decoratesActualConstraintAndIndexOwnersWhenRequested() {
		final Table table = new Table("items").setSchemaName("table_owner");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		final var constraint = new com.sqlapp.data.schemas.UniqueConstraint("uq", false);
		constraint.getColumns().add(table.getColumns().get(0));
		constraint.getSpecifics().put(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER, "actual_owner");
		table.getConstraints().add(constraint);
		table.getIndexes().add("ix", table.getColumns().get(0));
		table.getIndexes().get(0).getSpecifics().put(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER,
				"index_owner");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		final var operations = registry.createSql(table, SqlType.CREATE);
		assertTrue(operations.get(0).getSqlText().contains("\"actual_owner\".\"uq\""));
		assertTrue(operations.get(1).getSqlText().contains("\"index_owner\".\"ix\""));
	}

	@Test
	void preservesCustomAccessMethodParametersAndOperatorClassesThroughXml() throws Exception {
		final Table table = new Table("documents");
		table.getColumns().add("Odd Column", c -> c.setDataType(DataType.VARCHAR).setLength(200));
		final var index = new com.sqlapp.data.schemas.Index("ix").setIndexType(com.sqlapp.data.schemas.IndexType.Other);
		index.setTableSpaceName("text_space");
		index.getSpecifics()
				.put(com.sqlapp.data.db.dialect.informix.metadata.InformixIndexReader.INFORMIX_ACCESS_METHOD, "bts");
		index.getSpecifics().put(
				com.sqlapp.data.db.dialect.informix.metadata.InformixIndexReader.INFORMIX_ACCESS_METHOD_OWNER,
				"informix");
		index.getSpecifics().put(
				com.sqlapp.data.db.dialect.informix.metadata.InformixIndexReader.INFORMIX_ACCESS_PARAMETERS,
				"analyzer='keyword'");
		index.getColumns().add(table.getColumns().get(0), com.sqlapp.data.schemas.Order.Desc);
		index.getColumns().get(0).getSpecifics().put(
				com.sqlapp.data.db.dialect.informix.metadata.InformixIndexReader.INFORMIX_OPERATOR_CLASS,
				"bts_varchar_ops");
		index.getColumns().get(0).getSpecifics().put(
				com.sqlapp.data.db.dialect.informix.metadata.InformixIndexReader.INFORMIX_OPERATOR_CLASS_OWNER,
				"informix");
		table.getIndexes().add(index);
		final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final String sql = registry.createSql(restored, SqlType.CREATE).get(1).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains("\"Odd Column\" \"informix\".\"bts_varchar_ops\" DESC"), sql);
		assertTrue(sql.contains("IN \"text_space\" USING \"informix\".\"bts\" (analyzer='keyword')"), sql);
		index.getSpecifics()
				.remove(com.sqlapp.data.db.dialect.informix.metadata.InformixIndexReader.INFORMIX_ACCESS_METHOD);
		index.setIndexType(com.sqlapp.data.schemas.IndexType.BTree);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	private Table fragmented(final String strategy, final String expression) {
		final Table table = new Table("fragments");
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		final Partitioning partitioning = new Partitioning();
		partitioning.getSpecifics().put(INFORMIX_FRAGMENT_STRATEGY, strategy);
		final Partition partition = new Partition("p1").setTableSpaceName("rootdbs");
		partition.getSpecifics().put(INFORMIX_FRAGMENT_EXPRESSION, expression);
		partitioning.getPartitions().add(partition);
		table.setPartitioning(partitioning);
		return table;
	}

	@Test
	void placesConstraintNameAfterPrimaryKeyDefinition() {
		final Table table = new Table("CHECKPOINT");
		final Column id = new Column("ID").setDataType(DataType.VARCHAR).setLength(255).setNotNull(true);
		table.getColumns().add(id);
		table.setPrimaryKey("PK_CHECKPOINT", id);

		final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ").toUpperCase();

		assertTrue(sql.contains("PRIMARY KEY ( ID ) CONSTRAINT PK_CHECKPOINT"), sql);
		assertFalse(sql.contains("CONSTRAINT PK_CHECKPOINT PRIMARY KEY"), sql);
	}
}
