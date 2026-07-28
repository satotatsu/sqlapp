/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;

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
}
