/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import java.util.Locale;
import java.util.Set;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateViewFactory;
import com.sqlapp.data.schemas.View;
import com.sqlapp.util.CommonUtils;

/** MySQL view SQL using the query and output columns in the Schema model. */
public class MySqlCreateViewFactory extends AbstractCreateViewFactory<MySqlSqlBuilder> {

	public static final String CHECK_OPTION = "check_option";
	public static final String SECURITY_TYPE = "security_type";

	@Override
	protected void addCreateObject(View view, MySqlSqlBuilder builder) {
		if (!CommonUtils.isEmpty(view.getDefinition())) {
			builder._add(view.getDefinition());
			return;
		}
		if (CommonUtils.isEmpty(view.getStatement())) {
			throw new IllegalArgumentException(
					"MySQL view requires a query statement or complete definition: " + view.getName());
		}
		String security = option(view, SECURITY_TYPE, Set.of("DEFINER", "INVOKER"));
		String check = option(view, CHECK_OPTION, Set.of("NONE", "LOCAL", "CASCADED"));
		builder.create();
		if (security != null) {
			builder.space()._add("SQL SECURITY ")._add(security);
		}
		builder.view().name(view, getOptions().isDecorateSchemaName());
		if (!view.getColumns().isEmpty()) {
			builder.space()._add("(");
			for (int i = 0; i < view.getColumns().size(); i++) {
				builder.comma(i > 0).name(view.getColumns().get(i).getName());
			}
			builder._add(")");
		}
		builder.lineBreak().as().lineBreak()._add(view.getStatement());
		if (check != null && !"NONE".equals(check)) {
			builder.lineBreak()._add("WITH ")._add(check)._add(" CHECK OPTION");
		}
	}

	private String option(View view, String name, Set<String> allowed) {
		String value = view.getSpecifics().get(name);
		if (value == null) {
			return null;
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		if (!allowed.contains(normalized)) {
			throw new IllegalArgumentException("Invalid MySQL view " + name + ": " + value + "; expected " + allowed);
		}
		return normalized;
	}
}
