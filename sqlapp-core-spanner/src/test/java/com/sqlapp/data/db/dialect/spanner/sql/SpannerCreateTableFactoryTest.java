/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;

class SpannerCreateTableFactoryTest extends SpannerSqlFactoryTest {

	@Test
	void testPrimaryKeyOutsideColumnList() {
		final Table table = new Table("SINGERS");
		table.setDialect(dialect);
		final Column shardId = new Column("SHARD_ID")
				.setDataType(DataType.BIGINT).setNotNull(true);
		final Column singerId = new Column("SINGER_ID")
				.setDataType(DataType.UUID).setNotNull(true);
		table.getColumns().add(shardId);
		table.getColumns().add(singerId);
		table.getConstraints().addPrimaryKeyConstraint("PK_SINGERS",
				shardId, singerId).getColumns().get("SINGER_ID")
				.setOrder(Order.Desc);

		final String sql = sqlFactoryRegistry.createSql(table, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS SINGERS"), sql);
		assertTrue(sql.contains("SHARD_ID INT64"), sql);
		assertTrue(sql.contains("SINGER_ID UUID"), sql);
		assertTrue(sql.contains(
				") PRIMARY KEY ( SHARD_ID, SINGER_ID DESC )"), sql);
	}

	@Test
	void testUniqueConstraintAsUniqueIndex() {
		final Table table = new Table("USERS");
		table.setDialect(dialect);
		final Column id = new Column("ID")
				.setDataType(DataType.BIGINT).setNotNull(true);
		final Column email = new Column("EMAIL")
				.setDataType(DataType.VARCHAR).setLength(320)
				.setNotNull(true);
		table.getColumns().add(id);
		table.getColumns().add(email);
		table.getConstraints().addPrimaryKeyConstraint("PK_USERS", id);
		table.getConstraints().addUniqueConstraint("UK_USERS_EMAIL", email);

		final var operations = sqlFactoryRegistry.createSql(table,
				SqlType.CREATE);
		assertEquals(2, operations.size());
		final String tableSql = operations.get(0).getSqlText()
				.replaceAll("\\s+", " ");
		final String indexSql = operations.get(1).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(!tableSql.contains("UNIQUE"), tableSql);
		assertTrue(indexSql.contains(
				"CREATE UNIQUE INDEX IF NOT EXISTS UK_USERS_EMAIL ON USERS ( EMAIL )"),
				indexSql);
	}

	@Test
	void testRejectUnnamedUniqueConstraint() {
		final Table table = new Table("USERS");
		table.setDialect(dialect);
		final Column id = new Column("ID").setDataType(DataType.BIGINT);
		final Column email = new Column("EMAIL")
				.setDataType(DataType.VARCHAR).setLength(320);
		table.getColumns().add(id);
		table.getColumns().add(email);
		table.getConstraints().addPrimaryKeyConstraint("PK_USERS", id);
		table.getConstraints().addUniqueConstraint(null, email);

		assertThrows(IllegalArgumentException.class,
				() -> sqlFactoryRegistry.createSql(table, SqlType.CREATE));
	}

	@Test
	void testCreateNullFilteredStoringIndex() {
		final Table table = new Table("USERS");
		table.setDialect(dialect);
		final Column email = new Column("EMAIL")
				.setDataType(DataType.VARCHAR).setLength(320);
		final Column displayName = new Column("DISPLAY_NAME")
				.setDataType(DataType.VARCHAR).setLength(100);
		table.getColumns().add(email);
		table.getColumns().add(displayName);
		final Index index = new Index("IDX_USERS_EMAIL", email)
				.setUnique(true);
		index.getIncludes().add(displayName);
		index.getSpecifics().put(
				SpannerCreateIndexFactory.IS_NULL_FILTERED, true);
		table.getIndexes().add(index);

		final String sql = sqlFactoryRegistry.createSql(index, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains(
				"CREATE UNIQUE NULL_FILTERED INDEX IF NOT EXISTS IDX_USERS_EMAIL ON USERS ( EMAIL ) STORING ( DISPLAY_NAME )"),
				sql);
	}

	@Test
	void testAllowCommitTimestampColumn() {
		final Table table = new Table("EVENTS");
		table.setDialect(dialect);
		final Column id = new Column("ID").setDataType(DataType.BIGINT);
		final Column committedAt = new Column("COMMITTED_AT")
				.setDataType(DataType.TIMESTAMP).setNotNull(true);
		committedAt.getSpecifics().put(
				SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP, true);
		table.getColumns().add(id);
		table.getColumns().add(committedAt);
		table.getConstraints().addPrimaryKeyConstraint("PK_EVENTS", id);

		final String sql = sqlFactoryRegistry.createSql(table, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains(
				"COMMITTED_AT TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)"),
				sql);
	}

	@Test
	void testRejectCommitTimestampOnNonTimestampColumn() {
		final Table table = new Table("EVENTS");
		table.setDialect(dialect);
		final Column id = new Column("ID").setDataType(DataType.BIGINT);
		id.getSpecifics().put(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP,
				true);
		table.getColumns().add(id);
		table.getConstraints().addPrimaryKeyConstraint("PK_EVENTS", id);

		assertThrows(IllegalArgumentException.class,
				() -> sqlFactoryRegistry.createSql(table, SqlType.CREATE));
	}
}
