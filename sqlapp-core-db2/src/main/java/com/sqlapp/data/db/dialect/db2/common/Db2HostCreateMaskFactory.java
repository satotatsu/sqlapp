/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateMaskFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Mask;

/**
 * Host column mask: CREATE MASK ... FOR COLUMN ... RETURN ... ENABLE|DISABLE.
 * OR REPLACE is not emitted because Db2 for z/OS does not accept it. An
 * authoritative complete CREATE in {@code definition} is replayed verbatim.
 */
public class Db2HostCreateMaskFactory extends AbstractCreateMaskFactory<Db2SqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Mask mask) {
		if ("N".equals(mask.getSpecifics().get("SOURCE_VALID")))
			throw new IllegalArgumentException("Cannot recreate an invalid Db2 column mask: " + mask.getName());
		List<SqlOperation> result = new ArrayList<>();
		var builder = createSqlBuilder();
		addCreateObject(mask, builder);
		addSql(result, builder, SqlType.CREATE, mask);
		if (mask.getRemarks() != null && !mask.getRemarks().isEmpty() && mask.getDefinition().isEmpty()) {
			var comment = createSqlBuilder();
			comment._add("COMMENT ON MASK " + Db2CreationContext.qualified(mask.getSchemaName(), mask.getName())
					+ " IS '" + mask.getRemarks().replace("'", "''") + "'");
			addSql(result, comment, SqlType.SET_COMMENT, mask);
		}
		return result;
	}

	@Override
	protected void addCreateObject(final Mask mask, final Db2SqlBuilder builder) {
		if (!mask.getDefinition().isEmpty()) {
			String definition = String.join("\n", mask.getDefinition());
			if (!definition.stripLeading().matches("(?is)CREATE\\s+(?:OR\\s+REPLACE\\s+)?MASK\\b.*"))
				throw new IllegalArgumentException("Mask definition must be a complete CREATE MASK: " + mask.getName());
			builder._add(definition);
			return;
		}
		if (mask.getStatement().isEmpty() || String.join("\n", mask.getStatement()).isBlank())
			throw new IllegalArgumentException("Db2 column mask requires a RETURN expression: " + mask.getName());
		if (mask.getColumnName() == null || mask.getColumnName().isBlank() || mask.getTableName() == null)
			throw new IllegalArgumentException("Db2 column mask requires its table and column: " + mask.getName());
		String tableSchema = mask.getTableSchemaName() == null ? mask.getSchemaName() : mask.getTableSchemaName();
		builder._add("CREATE MASK " + Db2CreationContext.qualified(mask.getSchemaName(), mask.getName()) + " ON "
				+ Db2CreationContext.qualified(tableSchema, mask.getTableName()));
		String correlation = mask.getSpecifics().get("TABCORRELATION");
		if (correlation != null && !correlation.isBlank())
			builder._add(" AS " + Db2CreationContext.identifier(correlation));
		builder._add(" FOR COLUMN " + Db2CreationContext.identifier(mask.getColumnName()) + " RETURN");
		builder.appendIndent(1);
		builder.lineBreak()._add(String.join("\n", mask.getStatement()).strip());
		builder.appendIndent(-1);
		builder.lineBreak()._add(mask.isEnable() ? "ENABLE" : "DISABLE");
	}
}
