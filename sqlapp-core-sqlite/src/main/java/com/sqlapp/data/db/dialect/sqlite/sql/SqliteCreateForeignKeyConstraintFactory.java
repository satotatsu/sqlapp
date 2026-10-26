/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateForeignKeyConstraintFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * SQLite foreign keys belong to CREATE TABLE and use unqualified parent names.
 */
public class SqliteCreateForeignKeyConstraintFactory
		extends AbstractCreateForeignKeyConstraintFactory<SqliteSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(ForeignKeyConstraint constraint) {
		throw new IllegalArgumentException("SQLite cannot ADD a foreign key to an existing table; recreate the table: "
				+ constraint.getTableName());
	}

	@Override
	public void addObjectDetail(ForeignKeyConstraint constraint, Table table, SqliteSqlBuilder builder) {
		String schema = table == null || table.getSchemaName() == null ? "main" : table.getSchemaName();
		String relatedSchema = constraint.getRelatedTableSchemaName();
		if (relatedSchema != null && !schema.equalsIgnoreCase(relatedSchema))
			throw new IllegalArgumentException(
					"SQLite foreign keys cannot reference another database: " + schema + " -> " + relatedSchema);
		if (constraint.getColumns().isEmpty() || CommonUtils.isEmpty(constraint.getRelatedTableName()))
			throw new IllegalArgumentException("SQLite foreign key requires child columns and a parent table");
		if (!constraint.getRelatedColumns().isEmpty()
				&& constraint.getRelatedColumns().size() != constraint.getColumns().size())
			throw new IllegalArgumentException("SQLite foreign key child and parent column counts must match");
		if (!CommonUtils.isEmpty(constraint.getName()))
			builder.constraint().name(constraint, false).space();
		builder.foreignKey().space()._add("(").names(constraint.getColumns())._add(")").references()
				.name(constraint.getRelatedTableName());
		if (!constraint.getRelatedColumns().isEmpty())
			builder.space()._add("(").names(constraint.getRelatedColumns())._add(")");
		if (constraint.getUpdateRule() != null)
			builder.space()._add("ON UPDATE ")._add(action(constraint.getUpdateRule()));
		if (constraint.getDeleteRule() != null)
			builder.space()._add("ON DELETE ")._add(action(constraint.getDeleteRule()));
		if (constraint.getDeferrability() == Deferrability.NotDeferrable)
			builder.space()._add("NOT DEFERRABLE");
		else if (constraint.getDeferrability() != null)
			builder.space()._add("DEFERRABLE ")._add(constraint.getDeferrability());
	}

	private String action(CascadeRule rule) {
		return rule == CascadeRule.None ? "NO ACTION" : rule.getSqlValue();
	}
}
