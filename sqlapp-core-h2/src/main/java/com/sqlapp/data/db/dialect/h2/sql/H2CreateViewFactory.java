/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateViewFactory;
import com.sqlapp.data.schemas.View;

/** Preserve separately modeled view output columns with their query body. */
public class H2CreateViewFactory extends AbstractCreateViewFactory<H2SqlBuilder> {
	@Override
	protected void addCreateObject(View view, H2SqlBuilder builder) {
		if (!view.getDefinition().isEmpty()) {
			builder._add(view.getDefinition());
			return;
		}
		if (view.getStatement().isEmpty())
			throw new IllegalArgumentException("H2 view requires a statement or complete CREATE definition");
		builder.create().view().ifNotExists(getOptions().isCreateIfNotExists()).name(view,
				getOptions().isDecorateSchemaName());
		if (!view.getColumns().isEmpty()) {
			builder.space()._add("(");
			for (int i = 0; i < view.getColumns().size(); i++) {
				if (i > 0)
					builder._add(",");
				builder.name(view.getColumns().get(i).getName());
			}
			builder._add(")");
		}
		builder.as().lineBreak()._add(view.getStatement());
	}
}
