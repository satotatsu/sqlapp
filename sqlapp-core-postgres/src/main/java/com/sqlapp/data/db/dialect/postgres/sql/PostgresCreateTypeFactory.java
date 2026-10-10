/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateNamedObjectFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.util.CommonUtils;

/**
 * Recreates composite types, preferring the complete definition read from the
 * catalog.
 */
public class PostgresCreateTypeFactory extends AbstractCreateNamedObjectFactory<Type, PostgresSqlBuilder> {
	@Override
	protected void addCreateObject(final Type obj, PostgresSqlBuilder builder) {
		if (!CommonUtils.isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
			return;
		}
		builder.create().space()._add("TYPE").space().name(obj, getOptions().isDecorateSchemaName()).as().space()
				._add("(");
		boolean first = true;
		for (var column : obj.getColumns()) {
			builder.comma(!first).name(column).space().typeDefinition(column.getDataType(), column.getDataTypeName(),
					column.getLength(), column.getScale());
			if (column.getArrayDimension() > 0)
				builder._add("[]".repeat(column.getArrayDimension()));
			first = false;
		}
		builder._add(")");
	}

	@Override
	protected void addOptions(final Type obj, List<SqlOperation> result) {
		if (obj.getRemarks() != null) {
			PostgresSqlBuilder builder = createSqlBuilder();
			builder.comment().on().space()._add("TYPE").space().name(obj, getOptions().isDecorateSchemaName()).is()
					.sqlChar(obj.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, obj);
		}
		obj.getColumns().stream().filter(c -> c.getRemarks() != null).forEach(column -> {
			PostgresSqlBuilder builder = createSqlBuilder();
			builder.comment().on().column().space().name(obj, getOptions().isDecorateSchemaName())._add(".")
					.name(column).is().sqlChar(column.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, column);
		});
	}
}
