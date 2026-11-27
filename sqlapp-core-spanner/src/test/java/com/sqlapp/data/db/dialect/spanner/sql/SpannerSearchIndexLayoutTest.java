/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SpannerSearchIndexLayoutTest {
	private Table table() {
		final var table = new Table("events");
		table.getColumns().add(new Column("tokens").setDataType(DataType.OTHER).setDataTypeName("TOKENLIST"));
		table.getColumns().add(new Column("tenant").setDataType(DataType.BIGINT));
		table.getColumns().add(new Column("time").setDataType(DataType.BIGINT));
		final var index = new Index("search").setIndexType(IndexType.FullText);
		index.getColumns().add(new ReferenceColumn("tokens"));
		table.getIndexes().add(index);
		return table;
	}

	@Test
	void supportsSimpleOptionalLayoutWithDefaultAscendingOrder() throws Exception {
		final var table = table();
		final var index = table.getIndexes().get(0);
		final var partitioning = new Partitioning();
		partitioning.getPartitioningColumns().add(new ReferenceColumn("tenant"));
		index.setPartitioning(partitioning);
		index.getSpecifics().put(SpannerCreateIndexFactory.SEARCH_ORDER_BY_COLUMN, "time");
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("PARTITION BY `tenant` ORDER BY `time` ASC"), sql);
	}

	@Test
	void rejectsInvalidOrderAndUnsupportedLayoutRatherThanDroppingIt() {
		for (int mode = 0; mode < 5; mode++) {
			final var table = table();
			final var index = table.getIndexes().get(0);
			index.getSpecifics().put(SpannerCreateIndexFactory.SEARCH_ORDER_BY_COLUMN, "time");
			switch (mode) {
			case 0 -> index.getSpecifics().put(SpannerCreateIndexFactory.SEARCH_ORDER_BY_COLUMN, "missing");
			case 1 -> index.getSpecifics().put(SpannerCreateIndexFactory.SEARCH_ORDER_BY_DIRECTION, "invalid");
			case 2 -> table.getColumns().get("time").setDataType(DataType.VARCHAR);
			case 3 -> {
				index.getSpecifics().remove(SpannerCreateIndexFactory.SEARCH_ORDER_BY_COLUMN);
				index.getSpecifics().put(SpannerCreateIndexFactory.SEARCH_ORDER_BY_DIRECTION, "DESC");
			}
			case 4 -> {
				index.getSpecifics().put(SpannerCreateIndexFactory.PARENT_TABLE_NAME, "Parents");
				index.getSpecifics().put(SpannerCreateIndexFactory.SORT_ORDER_SHARDING, false);
			}
			}
			assertThrows(IllegalArgumentException.class,
					() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE));
		}
		final var table = table();
		final var index = table.getIndexes().get(0);
		index.setPartitioning(new Partitioning(PartitioningType.Hash));
		assertThrows(UnsupportedOperationException.class,
				() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE));
		index.setIndexType(IndexType.BTree);
		assertThrows(UnsupportedOperationException.class,
				() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE));
	}
}
