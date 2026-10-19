/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateViewFactory;
import com.sqlapp.data.schemas.View;
import com.sqlapp.util.CommonUtils;

/** SQLite views retain their declared column names and database identity. */
public class SqliteCreateViewFactory extends AbstractCreateViewFactory<SqliteSqlBuilder> {
	@Override
	protected void addCreateObject(View view, SqliteSqlBuilder builder) {
		if (!CommonUtils.isEmpty(view.getDefinition())) {
			builder._add(SqliteDdl.withIdentity(toString(view.getDefinition()), "VIEW", view.getSchemaName(),
					view.getName()));
			return;
		}
		if (CommonUtils.isEmpty(view.getStatement()))
			throw new IllegalArgumentException("SQLite view requires statement or definition: " + view.getName());
		builder.create().view().name(view, true);
		if (!view.getColumns().isEmpty()) {
			builder._add(" (");
			for (int i = 0; i < view.getColumns().size(); i++) {
				if (i > 0)
					builder._add(", ");
				builder.name(view.getColumns().get(i).getName());
			}
			builder._add(")");
		}
		builder.lineBreak().as().lineBreak()._add(view.getStatement());
	}
}
