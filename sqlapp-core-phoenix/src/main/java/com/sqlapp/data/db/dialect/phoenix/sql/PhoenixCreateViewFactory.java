/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import com.sqlapp.data.db.sql.AbstractCreateViewFactory;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.schemas.View;

public class PhoenixCreateViewFactory extends AbstractCreateViewFactory<PhoenixSqlBuilder> {
	@Override
	protected void addCreateObject(View view, PhoenixSqlBuilder builder) {
		if (!view.getDefinition().isEmpty()) {
			super.addCreateObject(view, builder);
			return;
		}
		if ("true".equalsIgnoreCase(view.getSpecifics().get("phoenix.viewMetadataOnly")))
			throw new UnsupportedOperationException(
					"Phoenix VIEW_STATEMENT does not describe declared view columns or mapped-view DDL; "
							+ "provide a complete CREATE VIEW definition: " + view.getName());
		if (view.getStatement().isEmpty() || String.join("\n", view.getStatement()).isBlank())
			throw new IllegalArgumentException(
					"Phoenix view requires a statement or complete CREATE definition: " + view.getName());
		if (!view.getConstraints().isEmpty())
			throw new UnsupportedOperationException(
					"Phoenix views with constraints require complete native CREATE DDL: " + view.getName());
		createObject(view, builder);
		builder.name(view, getOptions().isDecorateSchemaName());
		if (!view.getColumns().isEmpty()) {
			builder.space()._add("(");
			int count = 0;
			for (final var column : view.getColumns()) {
				builder.comma(count++ > 0);
				PhoenixViewColumns.appendDefinition(column, builder);
			}
			builder._add(")");
		}
		builder.lineBreak().as().lineBreak()._add(view.getStatement());
	}

	@Override
	protected void createObject(View view, PhoenixSqlBuilder builder) {
		builder.create().view().ifNotExists(getOptions().isCreateIfNotExists());
	}
}
