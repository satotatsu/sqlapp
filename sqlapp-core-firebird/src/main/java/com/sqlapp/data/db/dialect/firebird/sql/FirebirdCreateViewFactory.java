/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.View;

/**
 * Explicit view columns preserve aliases independent of the SELECT expression
 * names.
 */
public class FirebirdCreateViewFactory extends AbstractCreateViewFactory<FirebirdSqlBuilder> {
	@Override
	protected void addCreateObject(View view, FirebirdSqlBuilder builder) {
		if (!view.getDefinition().isEmpty()) {
			builder._add(view.getDefinition());
			return;
		}
		if (view.getStatement().isEmpty())
			throw new IllegalArgumentException("Firebird view requires a statement or complete definition");
		builder._add("CREATE VIEW ").name(view.getName());
		if (!view.getColumns().isEmpty()) {
			builder._add(" (");
			for (int i = 0; i < view.getColumns().size(); i++)
				builder.comma(i > 0).name(view.getColumns().get(i).getName());
			builder._add(")");
		}
		builder._add(" AS ")._add(view.getStatement());
	}

	@Override
	protected void addOtherDefinitions(View view, java.util.List<SqlOperation> result) {
		if (view.getRemarks() != null)
			result.addAll(getSqlFactoryRegistry().createSql(view, SqlType.SET_COMMENT));
		for (var column : view.getColumns())
			if (column.getRemarks() != null)
				result.addAll(getSqlFactoryRegistry().createSql(column, SqlType.SET_COMMENT));
	}
}
