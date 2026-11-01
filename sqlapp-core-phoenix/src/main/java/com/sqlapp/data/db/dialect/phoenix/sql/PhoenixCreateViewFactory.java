/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import com.sqlapp.data.db.sql.AbstractCreateViewFactory;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.schemas.View;

public class PhoenixCreateViewFactory extends AbstractCreateViewFactory<PhoenixSqlBuilder> {
	@Override
	protected void addCreateObject(View view, PhoenixSqlBuilder builder) {
		if (view.getDefinition().isEmpty()) {
			if ("true".equalsIgnoreCase(view.getSpecifics().get("phoenix.viewMetadataOnly")))
				throw new UnsupportedOperationException(
						"Phoenix VIEW_STATEMENT does not describe declared view columns or mapped-view DDL; "
								+ "provide a complete CREATE VIEW definition: " + view.getName());
			if (view.getStatement().isEmpty())
				throw new IllegalArgumentException(
						"Phoenix view requires a statement or complete CREATE definition: " + view.getName());
			if (!view.getColumns().isEmpty())
				throw new UnsupportedOperationException(
						"Phoenix views with modeled columns require complete native CREATE DDL: " + view.getName());
		}
		super.addCreateObject(view, builder);
	}

	@Override
	protected void createObject(View view, PhoenixSqlBuilder builder) {
		builder.create().view().ifNotExists(getOptions().isCreateIfNotExists());
	}
}
