/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * Uppercase names are common when migrating from other databases; keywords and
 * names that cannot start an unquoted identifier must still be quoted.
 */
class SqliteIdentifierQuotingTest {
	@Test
	void quotesKeywordsAndNamesThatCannotStartAnIdentifier() {
		var dialect = DialectHolder.defaultDialect;
		assertEquals("[ORDER]", dialect.quote("ORDER"));
		assertEquals("[GROUP]", dialect.quote("GROUP"));
		assertEquals("[order]", dialect.quote("order"));
		assertEquals("[1ST]", dialect.quote("1ST"));
		assertEquals("[$AMOUNT]", dialect.quote("$AMOUNT"));
		assertEquals("CUSTOMER", dialect.quote("CUSTOMER"));
		assertEquals("_ID", dialect.quote("_ID"));
		assertEquals("[ORDER]", dialect.quote("[ORDER]"));
		assertTrue(dialect.needQuote("TABLE"));
		assertFalse(dialect.needQuote("CUSTOMER"));
	}

	@Test
	void createsTableWhoseUppercaseNamesAreKeywords() throws Exception {
		var dialect = DialectHolder.defaultDialect;
		var table = new Table("ORDER");
		table.getColumns().add(new Column("GROUP").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("1ST").setDataType(DataType.BIGINT));
		table.getColumns().add(new Column("ID").setDataType(DataType.BIGINT));
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			for (var operation : dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			List<String> names = new ArrayList<>();
			try (var rs = statement.executeQuery("SELECT name FROM pragma_table_info('ORDER') ORDER BY cid")) {
				while (rs.next())
					names.add(rs.getString(1));
			}
			assertEquals(List.of("GROUP", "1ST", "ID"), names);
		}
	}
}
