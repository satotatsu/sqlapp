/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PhoenixIdentifierValidationTest {
	@Test
	void escapesEmbeddedQuotesAndPreservesAlreadyQuotedNames() {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect5_3_1)) {
			assertEquals("\"a\"\"b\"", dialect.quote("a\"b"));
			assertEquals("\"a\"\"b\"", dialect.quote("\"a\"\"b\""));
			assertEquals("", dialect.quote(""));
			assertNull(dialect.quote(null));
		}
	}

	@Test
	void rejectsMissingPrimaryKeyColumnsInsteadOfEmittingInvalidDdl() {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect5_3_1)) {
			final var table = new Table("events");
			table.getColumns().add(new Column("Id").setDataType(DataType.INT));
			final var pk = new UniqueConstraint("pk").setPrimaryKey(true);
			pk.getColumns().add("id");
			table.getConstraints().add(pk);
			assertThrows(IllegalArgumentException.class,
					() -> dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
			pk.getColumns().clear();
			pk.getColumns().add("Id");
			assertFalse(dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).isEmpty());
		}
	}

	@Test
	void rejectsBlankIndexKeysIncludesAndFamilies() {
		for (boolean include : List.of(false, true)) {
			for (boolean blankName : List.of(false, true)) {
				final var table = new Table("events");
				final var index = new Index("idx");
				index.getColumns().add("id");
				final var columns = include ? index.getIncludes() : index.getColumns();
				if (!include)
					columns.clear();
				columns.add(blankName ? " " : "payload");
				if (!blankName)
					columns.get(0).getSpecifics().put(PhoenixCreateTableFactory.COLUMN_FAMILY, " ");
				table.getIndexes().add(index);
				assertThrows(IllegalArgumentException.class,
						() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE));
			}
		}
	}

	@Test
	void preservesExplicitQuotedIndexFamiliesThroughXml() throws Exception {
		final var table = new Table("events");
		final var index = new Index("idx");
		index.getColumns().add("id");
		index.getIncludes().add("payload");
		index.getColumns().get(0).getSpecifics().put(PhoenixCreateTableFactory.COLUMN_FAMILY, "a\"b");
		index.getIncludes().get(0).getSpecifics().put(PhoenixCreateTableFactory.COLUMN_FAMILY, "case sensitive");
		table.getIndexes().add(index);
		final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setQuateColumnName(true);
		final String sql = registry.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("\"a\"\"b\".\"id\""), sql);
		assertTrue(sql.contains("\"case sensitive\".\"payload\""), sql);
	}
}
