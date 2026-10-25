/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Table;

/**
 * Db2 views retain their creation context and optional modeled column names.
 */
public class Db2CreateViewFactory extends Db2CreateTableFactory {
	@Override
	public List<SqlOperation> createSql(Table view) {
		List<SqlOperation> result = new ArrayList<>();
		for (String context : Db2CreationContext.sql(view.getSpecifics().get("QUALIFIER"),
				view.getSpecifics().get("FUNC_PATH")))
			addSql(result, createSqlBuilder()._add(context), SqlType.SET_SEARCH_PATH_TO_SCHEMA, view);
		if (!view.getDefinition().isEmpty()) {
			for (String definition : Db2DefinitionSql.statements(view.getDefinition(), getDialect())) {
				validateText(definition, view);
				addSql(result, createSqlBuilder()._add(definition), SqlType.CREATE, view);
			}
		} else {
			String kind = view.getSpecifics().get("DB2_VIEW_TYPE");
			if (kind != null && !"V".equals(kind))
				throw new IllegalArgumentException(
						"Db2 typed view requires complete CREATE definition: " + view.getName());
			if (view.getStatement().isEmpty())
				throw new IllegalArgumentException(
						"Db2 view requires complete definition or query statement: " + view.getName());
			Db2DefinitionSql.text(view.getStatement());
			var builder = createSqlBuilder().create().view().space()._add(viewName(view));
			if (!view.getColumns().isEmpty()) {
				builder._add(" (");
				int i = 0;
				for (var column : view.getColumns())
					builder.comma(i++ > 0)._add(Db2CreationContext.identifier(column.getName()));
				builder._add(")");
			}
			builder.as().space()._add(view.getStatement());
			String check = view.getSpecifics().get("VIEWCHECK");
			if (check != null && !"N".equals(check)) {
				if (!Set.of("L", "C").contains(check) || Boolean.TRUE.equals(view.getReadonly()))
					throw new IllegalArgumentException(
							"Invalid Db2 view CHECK OPTION or read-only view: " + view.getName());
				builder._add(" WITH " + ("L".equals(check) ? "LOCAL" : "CASCADED") + " CHECK OPTION");
			}
			addSql(result, builder, SqlType.CREATE, view);
		}
		addOtherDefinitions(view, result);
		return result;
	}

	@Override
	protected void addOtherDefinitions(Table view, List<SqlOperation> result) {
		if (view.getRemarks() != null && !view.getRemarks().isEmpty())
			addSql(result,
					createSqlBuilder()._add("COMMENT ON TABLE " + viewName(view) + " IS ").sqlChar(view.getRemarks()),
					SqlType.SET_COMMENT, view);
		for (var column : view.getColumns())
			if (column.getRemarks() != null && !column.getRemarks().isEmpty())
				addSql(result,
						createSqlBuilder()
								._add("COMMENT ON COLUMN " + viewName(view) + "."
										+ Db2CreationContext.identifier(column.getName()) + " IS ")
								.sqlChar(column.getRemarks()),
						SqlType.SET_COMMENT, column);
	}

	private String viewName(Table view) {
		return getOptions().isDecorateSchemaName() && view.getSchemaName() != null
				? Db2CreationContext.qualified(view.getSchemaName(), view.getName())
				: Db2CreationContext.identifier(view.getName());
	}

	private void validateText(String text, Table view) {
		if (text == null || text.isBlank() || text.indexOf('\0') >= 0)
			throw new IllegalArgumentException("Db2 view requires executable definition/query text: " + view.getName());
	}
}
