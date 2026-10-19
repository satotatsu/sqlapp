/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.CommonUtils;

/** SQLite CREATE TABLE from the shared Schema model. */
public class SqliteCreateTableFactory extends AbstractCreateTableFactory<SqliteSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Table table) {
		if (getDialect() instanceof com.sqlapp.data.db.dialect.sqlite.Sqlite sqlite) {
			if ("true".equals(table.getSpecifics().get("strict")) && !sqlite.supportsStrictTables())
				throw new IllegalArgumentException("SQLite STRICT tables require SQLite 3.37 or later");
			if ("true".equals(table.getSpecifics().get("without_rowid")) && !sqlite.supportsWithoutRowid())
				throw new IllegalArgumentException("SQLite WITHOUT ROWID requires SQLite 3.8.2 or later");
		}
		var identities = table.getColumns().stream().filter(Column::isIdentity).toList();
		var primary = table.getConstraints().getPrimaryKeyConstraint();
		if (identities.size() > 1 || !identities.isEmpty() && ("true".equals(table.getSpecifics().get("without_rowid"))
				|| primary != null && (primary.getColumns().size() != 1
						|| !identities.get(0).getName().equals(primary.getColumns().get(0).getName()))))
			throw new IllegalArgumentException(
					"SQLite identity requires a single INTEGER primary key in a rowid table: " + table.getName());
		return super.createSql(table);
	}

	@Override
	protected void addCreateObject(Table table, SqliteSqlBuilder builder) {
		builder.create().table().name(table, table.getSchemaName() != null);
	}

	@Override
	protected void addColumnDefinition(Column column, SqliteSqlBuilder builder) {
		Column definition = column.clone();
		definition.setIdentity(false);
		String notNullConflict = column.getSpecifics().get("sqlite.notNull.onConflict");
		if (notNullConflict != null) {
			notNullConflict = notNullConflict.toUpperCase(java.util.Locale.ROOT);
			if (!java.util.Set.of("ROLLBACK", "ABORT", "FAIL", "IGNORE", "REPLACE").contains(notNullConflict))
				throw new IllegalArgumentException(
						"Invalid SQLite NOT NULL conflict policy for " + column.getName() + ": " + notNullConflict);
			definition.setNotNull(false);
		}
		String declaredType = column.getSpecifics().get("sqlite.declaredType");
		if (declaredType != null)
			definition.setDataType(DataType.OTHER).setDataTypeName(declaredType);
		builder.name(column).space().definition(definition, false);
		if (notNullConflict != null && column.isNotNull())
			builder.space()._add("NOT NULL ON CONFLICT ")._add(notNullConflict);
		if (!CommonUtils.isEmpty(column.getCollation()))
			builder.space()._add("COLLATE ").name(column.getCollation());
		if (isInlinePrimaryKey(column) && column.getTable() != null) {
			var primary = column.getTable().getConstraints().getPrimaryKeyConstraint();
			if (primary != null && !CommonUtils.isEmpty(primary.getName()))
				builder.space().constraint().name(primary, false);
		}
		if (column.isIdentity()) {
			String type = declaredType == null ? createSqlBuilder().addTypeDefinition(column).toString() : declaredType;
			if (!"INTEGER".equalsIgnoreCase(type.trim()))
				throw new IllegalArgumentException("SQLite identity requires INTEGER: " + column.getName());
			builder.space()._add("PRIMARY KEY");
			addPrimaryConflict(column, builder);
			if ("true".equals(column.getSpecifics().get("sqlite.autoincrement")))
				builder.space()._add("AUTOINCREMENT");
		}
		if ("true".equals(column.getSpecifics().get("sqlite.primaryKey.inlineDesc"))) {
			builder.space()._add("PRIMARY KEY DESC");
			addPrimaryConflict(column, builder);
		}
		if (!CommonUtils.isEmpty(column.getFormula())) {
			if (getDialect() instanceof com.sqlapp.data.db.dialect.sqlite.Sqlite sqlite
					&& !sqlite.supportsGeneratedColumns())
				throw new IllegalArgumentException("SQLite generated columns require SQLite 3.31 or later");
			if (column.isIdentity() || column.getDefaultValue() != null)
				throw new IllegalArgumentException(
						"SQLite generated column cannot have identity or default: " + column.getName());
			builder.space()._add("GENERATED ALWAYS AS (")._add(column.getFormula())._add(") ")
					._add(column.isFormulaPersisted() ? "STORED" : "VIRTUAL");
		}
	}

	@Override
	protected void addConstraintDefinition(Constraint constraint, SqliteSqlBuilder builder) {
		if (constraint instanceof UniqueConstraint unique) {
			Table table = unique.getTable();
			if (unique.isPrimaryKey() && unique.getColumns().size() == 1 && table != null
					&& isInlinePrimaryKey(table.getColumns().get(unique.getColumns().get(0).getName())))
				return;
			builder.lineBreak().comma();
			if (!CommonUtils.isEmpty(unique.getName()))
				builder.constraint().name(unique, false);
			builder.space()._add(unique.isPrimaryKey() ? "PRIMARY KEY" : "UNIQUE").space()._add("(");
			for (int i = 0; i < unique.getColumns().size(); i++) {
				var column = unique.getColumns().get(i);
				builder.comma(i > 0).name(column);
				String collation = unique.getSpecifics().get("sqlite.key.collation." + i);
				if (collation != null)
					builder.space()._add("COLLATE ").name(collation);
				if (column.getOrder() != null)
					builder.space()._add(column.getOrder());
			}
			builder._add(")");
			addConflict(unique.getSpecifics().get("sqlite.onConflict"), builder);
			return;
		}
		super.addConstraintDefinition(constraint, builder);
	}

	private void addPrimaryConflict(Column column, SqliteSqlBuilder builder) {
		if (column.getTable() != null) {
			var primary = column.getTable().getConstraints().getPrimaryKeyConstraint();
			if (primary != null)
				addConflict(primary.getSpecifics().get("sqlite.onConflict"), builder);
		}
	}

	private void addConflict(String policy, SqliteSqlBuilder builder) {
		if (policy == null)
			return;
		String normalized = policy.toUpperCase(java.util.Locale.ROOT);
		if (!java.util.Set.of("ROLLBACK", "ABORT", "FAIL", "IGNORE", "REPLACE").contains(normalized))
			throw new IllegalArgumentException("Invalid SQLite key conflict policy: " + policy);
		builder.space()._add("ON CONFLICT ")._add(normalized);
	}

	private boolean isInlinePrimaryKey(Column column) {
		return column != null
				&& (column.isIdentity() || "true".equals(column.getSpecifics().get("sqlite.primaryKey.inlineDesc")));
	}

	@Override
	protected void addCreateIndexDefinition(Table table, Index index, List<SqlOperation> result) {
		result.addAll(getSqlFactoryRegistry().createSql(index, SqlType.CREATE));
	}

	@Override
	protected void addOption(Table table, SqliteSqlBuilder builder) {
		boolean withoutRowid = "true".equals(table.getSpecifics().get("without_rowid"));
		boolean strict = "true".equals(table.getSpecifics().get("strict"));
		if (withoutRowid)
			builder.space()._add("WITHOUT ROWID");
		if (strict)
			builder._add(withoutRowid ? ", STRICT" : " STRICT");
	}
}
