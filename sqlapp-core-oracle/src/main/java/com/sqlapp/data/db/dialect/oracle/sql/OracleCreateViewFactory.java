/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import com.sqlapp.data.db.sql.CreateViewFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.View;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

public class OracleCreateViewFactory extends CreateViewFactory {
	public static final String CHECK_CONSTRAINT = "oracle.view.checkConstraint";
	public static final String READ_ONLY_CONSTRAINT = "oracle.view.readOnlyConstraint";
	@Override
	public List<SqlOperation> createDiffSql(com.sqlapp.data.schemas.DbObjectDifference difference) {
		if (difference.getState() == com.sqlapp.data.schemas.State.Unchanged) return List.of();
		if (difference.getState() != com.sqlapp.data.schemas.State.Modified) return super.createDiffSql(difference);
		View before = difference.getOriginal(View.class), after = difference.getTarget(View.class);
		if (OracleJsonDualityViewUtils.isJsonRelationalDualityView(before) || OracleJsonDualityViewUtils.isJsonRelationalDualityView(after))
			throw new IllegalArgumentException("Duality view replacement requires a dedicated migration plan");
		for (String property : difference.toDifference().getChangedProperties(getDialect()).keySet())
			if (!java.util.Set.of("statement", "definition", "columns", "remarks", "specifics", CHECK_CONSTRAINT, READ_ONLY_CONSTRAINT).contains(property))
				throw new IllegalArgumentException("Unsupported Oracle view replacement property: " + property);
		if (!java.util.Objects.equals(before.getName(), after.getName()) || !java.util.Objects.equals(before.getSchemaName(), after.getSchemaName()))
			throw new IllegalArgumentException("View replacement must preserve owner and name");
		if (java.util.Objects.equals(before.getStatement(), after.getStatement()) && java.util.Objects.equals(before.getDefinition(), after.getDefinition())) {
			if (before.getColumns().size() != after.getColumns().size()) throw new IllegalArgumentException("Changing view column count requires changing its query or definition");
			for (int i = 0; i < before.getColumns().size(); i++) {
				var left = before.getColumns().get(i).clone(); var right = after.getColumns().get(i).clone();
				left.setName("COLUMN"); right.setName("COLUMN"); left.setRemarks(null); right.setRemarks(null);
				if (!left.diff(right).toDifference().getChangedProperties(getDialect()).isEmpty()) throw new IllegalArgumentException("Changing view column metadata requires changing its query or definition");
			}
		}
		for (String key : after.getSpecifics().keySet()) if (!CHECK_CONSTRAINT.equals(key) && !READ_ONLY_CONSTRAINT.equals(key)
				&& !java.util.Objects.equals(before.getSpecifics().get(key), after.getSpecifics().get(key)))
			throw new IllegalArgumentException("Unsupported Oracle view property change: " + key);
		boolean columnNamesChanged = before.getColumns().size() != after.getColumns().size();
		for (int i = 0; i < Math.min(before.getColumns().size(), after.getColumns().size()); i++)
			columnNamesChanged |= !java.util.Objects.equals(before.getColumns().get(i).getName(), after.getColumns().get(i).getName());
		boolean statementChanged = !java.util.Objects.equals(before.getStatement(), after.getStatement());
		boolean definitionChanged = !java.util.Objects.equals(before.getDefinition(), after.getDefinition());
		if (!CommonUtils.isEmpty(after.getDefinition()) && !definitionChanged && (statementChanged || columnNamesChanged))
			throw new IllegalArgumentException("Editing a view with a complete definition requires updating that definition");
		boolean replace = statementChanged || definitionChanged || columnNamesChanged || !java.util.Objects.equals(before.getSpecifics(), after.getSpecifics());
		List<SqlOperation> generated = createSql(after);
		String ddl = generated.get(0).getSqlText();
		if (!ddl.matches("(?is)^CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\b.*"))
			throw new IllegalArgumentException("View replacement requires a complete CREATE VIEW representation");
		if (!ddl.matches("(?is)^CREATE\\s+OR\\s+REPLACE\\s+.*")) ddl = ddl.replaceFirst("(?i)^CREATE\\s+", "CREATE OR REPLACE ");
		var result = new java.util.ArrayList<SqlOperation>();
		if (replace) result.add(createOperation(ddl, SqlType.ALTER, before, after));
		result.addAll(generated.subList(1, generated.size()));
		if (before.getRemarks() != null && after.getRemarks() == null) {
			var builder = createSqlBuilder(getDialect());
			builder.comment().on().table().space().name(after, getOptions().isDecorateSchemaName()).is().space().null_();
			addSql(result, builder, SqlType.SET_COMMENT, after);
		}
		for (int i = 0; i < Math.min(before.getColumns().size(), after.getColumns().size()); i++) {
			var column = after.getColumns().get(i);
			if (before.getColumns().get(i).getRemarks() != null && column.getRemarks() == null) {
				var builder = createSqlBuilder(getDialect());
				builder.comment().on().column().space().name(after, getOptions().isDecorateSchemaName())._add(".").name(column).is().space().null_();
				addSql(result, builder, SqlType.SET_COMMENT, column);
			}
		}
		return result;
	}
	@Override
	protected void addCreateObject(View view, AbstractSqlBuilder<?> builder) {
		String check = view.getSpecifics().get(CHECK_CONSTRAINT), readOnly = view.getSpecifics().get(READ_ONLY_CONSTRAINT);
		if (check != null && readOnly != null) throw new IllegalArgumentException("A view cannot combine CHECK OPTION and READ ONLY");
		if (!CommonUtils.isEmpty(view.getDefinition())) {
			if (check != null || readOnly != null) throw new IllegalArgumentException("Use a view statement with separately modeled restriction constraints");
			super.addCreateObject(view, builder); return;
		}
		if (CommonUtils.isEmpty(view.getStatement())) throw new IllegalArgumentException("Oracle view statement is required: " + view.getName());
		createObject(view, builder); builder.name(view, getOptions().isDecorateSchemaName());
		if (!view.getColumns().isEmpty()) {
			builder._add(" (");
			for (int i = 0; i < view.getColumns().size(); i++) builder.comma(i > 0).name(view.getColumns().get(i));
			builder._add(")");
		}
		builder.lineBreak().as().lineBreak()._add(view.getStatement());
		if (check != null || readOnly != null) {
			String constraint = check != null ? check : readOnly;
			builder.lineBreak()._add(check != null ? "WITH CHECK OPTION" : "WITH READ ONLY");
			if (!constraint.isBlank()) builder._add(" CONSTRAINT ").name(constraint);
		}
	}
	@Override
	protected void addOtherDefinitions(View view, List<SqlOperation> result) {
		if (view.getRemarks() != null) {
			var builder = createSqlBuilder(getDialect());
			builder.comment().on().table().space().name(view, getOptions().isDecorateSchemaName()).is().space().sqlChar(view.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, view);
		}
		view.getColumns().stream().filter(column -> column.getRemarks() != null).forEach(column -> {
			var builder = createSqlBuilder(getDialect());
			builder.comment().on().column().space().name(view, getOptions().isDecorateSchemaName())._add(".").name(column).is().space().sqlChar(column.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, column);
		});
	}
}
