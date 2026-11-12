/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.List;
import java.util.stream.Collectors;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Mview;

public class Postgres93CreateMviewFactory extends AbstractCreateMviewFactory<PostgresSqlBuilder> {
	@Override
	protected void addCreateObject(Mview view, PostgresSqlBuilder builder) {
		if (!view.getDefinition().isEmpty()) {
			builder._add(view.getDefinition());
			return;
		}
		if (view.getStatement().isEmpty() || String.join("\n", view.getStatement()).isBlank())
			throw new IllegalArgumentException("Materialized view requires a query or CREATE definition");
		builder.create().materialized().view().space().name(view, getOptions().isDecorateSchemaName());
		var aliases = view.getColumns().stream().filter(column -> !column.isHidden())
				.map(column -> "\"" + column.getName().replace("\"", "\"\"") + "\"").collect(Collectors.joining(", "));
		if (!aliases.isEmpty())
			builder._add(" (")._add(aliases)._add(")");
		if (view.getTableSpaceName() != null)
			builder._add(" TABLESPACE ").name(view.getTableSpaceName());
		builder.lineBreak().as().lineBreak()._add(view.getStatement());
	}

	@Override
	protected void addOtherDefinitions(Mview view, List<SqlOperation> operations) {
		for (var index : view.getIndexes())
			operations.addAll(getSqlFactoryRegistry().createSql(index, SqlType.CREATE));
		for (var column : view.getColumns()) {
			if (column.getRemarks() != null) {
				var builder = Postgres93AlterMviewFactory.columnComment(view, column, createSqlBuilder(),
						getOptions().isDecorateSchemaName());
				addSql(operations, builder, SqlType.SET_COMMENT, column);
			}
		}
		if (view.getRemarks() != null) {
			addSql(operations,
					Postgres93AlterMviewFactory.comment(view, createSqlBuilder(), getOptions().isDecorateSchemaName()),
					SqlType.SET_COMMENT, view);
		}
		if (view.getOwnerName() != null)
			operations.addAll(getSqlFactoryRegistry().createSql(view, SqlType.ALTER));
	}
}
