/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SapHanaCloudFuzzyIndexTest {
	@Test
	void separatesCloudAndPlatformCatalogQueries() {
		var platform = new SapHanaFulltextIndexReader(DialectHolder.defaultDialect);
		var cloud = new SapHanaFulltextIndexReader(DialectHolder.cloudDialect);
		assertTrue(platform.getSqlSqlNode(new ProductVersionInfo()).toString().contains("FULLTEXT_INDEXES"));
		assertFalse(cloud.getSqlSqlNode(new ProductVersionInfo()).toString().contains("FULLTEXT_INDEXES"));
		assertTrue(cloud.getSqlSqlNode(new ProductVersionInfo()).toString().contains("M_FUZZY_SEARCH_INDEXES"));
		assertTrue(new SapHanaIndexReader(DialectHolder.cloudDialect).getSqlSqlNode(new ProductVersionInfo()).toString()
				.contains("<> 'FUZZY SEARCH'"));
	}

	@Test
	void foldsPhysicalFuzzyIndexRowsAndRecreatesOptionsInOneQuery() {
		var queries = new AtomicInteger();
		var reader = new SapHanaFulltextIndexReader(DialectHolder.cloudDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				try {
					for (int partition = 0; partition < 2; partition++)
						handler.handleResultSetNext(new ExResultSet(null) {
							@Override
							public String getString(String key) {
								return switch (key.toUpperCase(java.util.Locale.ROOT)) {
								case "SCHEMA_NAME" -> "APP";
								case "TABLE_NAME" -> "DOCS";
								case "INDEX_NAME" -> "FT";
								case "SOURCE_COLUMN_NAME" -> "BODY";
								default -> null;
								};
							}

							@Override
							public Object getObject(String key) {
								return switch (key) {
								case "FUZZY_SEARCH_MODE" -> "TEXT";
								case "MIME_TYPE" -> "text/plain";
								case "TOKEN_SEPARATORS" -> "/,.-";
								default -> null;
								};
							}
						});
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		var indexes = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, queries.get());
		assertEquals(1, indexes.size());
		assertEquals(1, indexes.getFirst().getColumns().size());
		var table = new Table("DOCS").setSchemaName("APP");
		table.getColumns().add("BODY", c -> c.setDataType(DataType.NCLOB));
		table.getIndexes().add(indexes.getFirst());
		var registry = DialectHolder.cloudDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		String sql = registry.createSql(indexes.getFirst(), SqlType.CREATE).getFirst().getSqlText();
		assertTrue(sql.contains("ON APP.DOCS"), sql);
		assertTrue(sql.contains("SEARCH MODE TEXT"), sql);
		assertTrue(sql.contains("TOKEN SEPARATORS '/,.-'"), sql);
		assertTrue(sql.contains("MIME TYPE 'text/plain'"), sql);
	}
}
