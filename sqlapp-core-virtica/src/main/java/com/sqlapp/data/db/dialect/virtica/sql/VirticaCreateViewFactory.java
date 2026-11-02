/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateViewFactory;
import com.sqlapp.data.schemas.View;

/**
 * Creates a Vertica view without dropping its existing grants.
 */
public class VirticaCreateViewFactory extends AbstractCreateViewFactory<VirticaSqlBuilder> {
	@Override
	protected void addCreateObject(View view, VirticaSqlBuilder builder) {
		if (!com.sqlapp.util.CommonUtils.isEmpty(view.getDefinition())) {
			if (view.getDefinition().stream().allMatch(s -> s == null || s.isBlank()))
				throw new IllegalArgumentException("Vertica view CREATE definition must not be blank");
			builder._add(view.getDefinition());
			return;
		}
		if (com.sqlapp.util.CommonUtils.isEmpty(view.getStatement())
				|| view.getStatement().stream().allMatch(s -> s == null || s.isBlank()))
			throw new IllegalArgumentException(
					"Vertica view requires query or complete CREATE definition: " + view.getName());
		createObject(view, builder);
		boolean local = Boolean.TRUE.equals(view.getSpecifics().get("IS_LOCAL_TEMP_VIEW", Boolean.class));
		builder.name(view, !local && getOptions().isDecorateSchemaName());
		if (!view.getColumns().isEmpty()) {
			builder.space()._add("(");
			for (int i = 0; i < view.getColumns().size(); i++)
				builder.comma(i > 0).name(view.getColumns().get(i).getName());
			builder._add(")");
		}
		Boolean inherit = view.getSpecifics().get("INHERIT_PRIVILEGES", Boolean.class);
		if (!local && inherit != null && getDialect() instanceof com.sqlapp.data.db.dialect.virtica.Virtica11_1_1)
			builder.space()._add(inherit ? "INCLUDE SCHEMA PRIVILEGES" : "EXCLUDE SCHEMA PRIVILEGES");
		builder.lineBreak().as().lineBreak()._add(view.getStatement());
	}

	@Override
	protected void createObject(final View obj, final VirticaSqlBuilder builder) {
		builder.create().or().replace();
		if (Boolean.TRUE.equals(obj.getSpecifics().get("IS_LOCAL_TEMP_VIEW", Boolean.class)))
			builder.space()._add("LOCAL TEMPORARY");
		builder.view();
	}
}
