/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractInsertFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

/**
 * Preserve shared binding policies and handle a default-only INSERT natively.
 */
public class H2InsertFactory extends AbstractInsertFactory<H2SqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Table table) {
		var signature = createSqlSignature(table);
		if (table.getColumns().stream().anyMatch(column -> isInsertable(column) && (!column.isIdentity()
				|| getDialect().getIdentityInsertDefaultValue(column) != null && signature.hasNonNullValue(column))))
			return super.createSql(table);
		for (var column : table.getColumns())
			if (column.isIdentity() && signature.hasNonNullValue(column))
				throw new IllegalArgumentException(
						"H2 GENERATED ALWAYS identity does not accept explicit input: " + column.getName());
		if (getSqlType() == SqlType.INSERT_ROWS)
			throw new IllegalArgumentException(
					"H2 default-only batch input requires repeated INSERT DEFAULT VALUES statements");
		var builder = createSqlBuilder();
		builder.insert().into().name(table, getOptions().isDecorateSchemaName()).space()._add("DEFAULT VALUES");
		return List.of(new SqlOperation(builder.toString(), SqlType.INSERT, table));
	}

	public static class Rows extends H2InsertFactory {
		@Override
		protected SqlType getSqlType() {
			return SqlType.INSERT_ROWS;
		}

		@Override
		protected void addValues(H2SqlBuilder builder) {
			builder.lineBreak()._add("/*VALUES*/").values();
		}

		@Override
		protected void addInsertAfter(Table table, H2SqlBuilder builder) {
			builder._add("/*END*/");
		}
	}
}
