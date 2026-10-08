/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.util.AbstractSqlBuilder;

/** PostgreSQL CHECK and foreign-key generation options stored in Schema Specifics. */
public final class PostgresConstraintOptions {
	public static final String NOT_VALID = "notValid";
	public static final String NO_INHERIT = "noInherit";
	private PostgresConstraintOptions() { }
	interface NotValidFactory { }

	static void appendNoInherit(com.sqlapp.data.schemas.CheckConstraint constraint, AbstractSqlBuilder<?> builder, boolean supported) {
		String value = constraint.getSpecifics().get(NO_INHERIT);
		if (value != null && !"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
			throw new IllegalArgumentException("Constraint noInherit must be true or false.");
		}
		if (supported && Boolean.parseBoolean(value)) builder.space()._add("NO INHERIT");
	}

	static void constraintComment(Constraint constraint, com.sqlapp.data.schemas.Table table,
			AbstractSqlBuilder<?> builder, boolean decorateSchemaName) {
		builder.comment().on().constraint().space().name(constraint, false).on()
				.name(table, decorateSchemaName).is().space().sqlChar(constraint.getRemarks());
	}

	static void backingIndexComment(com.sqlapp.data.schemas.UniqueConstraint constraint,
			AbstractSqlBuilder<?> builder, boolean decorateSchemaName) {
		// ADD CONSTRAINT with a column list creates its index under the constraint name.
		var index = new com.sqlapp.data.schemas.Index(constraint.getName());
		index.setSchemaName(constraint.getTable().getSchemaName());
		builder.comment().on().index().space().name((com.sqlapp.data.schemas.AbstractSchemaObject<?>) index, decorateSchemaName).is().space()
				.sqlChar(backingIndexRemarks(constraint));
	}

	static String backingIndexRemarks(com.sqlapp.data.schemas.UniqueConstraint constraint) {
		if (constraint.getIndex().getRemarks() != null) return constraint.getIndex().getRemarks();
		var table = constraint.getTable();
		if (table == null) return null;
		var index = table.getIndexes().get(constraint.getIndexName() == null ? constraint.getName() : constraint.getIndexName());
		return index == null ? null : index.getRemarks();
	}

	static void appendIncludes(com.sqlapp.data.schemas.UniqueConstraint constraint, AbstractSqlBuilder<?> builder) {
		if (!constraint.getIndex().getIncludes().isEmpty()) {
			builder.space()._add("INCLUDE").space().brackets(() -> builder.names(constraint.getIndex().getIncludes()));
		}
	}

	static void appendNullsNotDistinct(com.sqlapp.data.schemas.UniqueConstraint constraint, AbstractSqlBuilder<?> builder) {
		String value = constraint.getSpecifics().get(PostgresCreateIndexFactory.NULLS_NOT_DISTINCT);
		if (value == null) value = constraint.getIndex().getSpecifics().get(PostgresCreateIndexFactory.NULLS_NOT_DISTINCT);
		if (!constraint.isPrimaryKey() && Boolean.parseBoolean(value)) builder.space()._add("NULLS NOT DISTINCT");
	}

	static void appendDeferrability(Constraint constraint, AbstractSqlBuilder<?> builder) {
		var mode = constraint.getDeferrability();
		if (mode != null && mode != com.sqlapp.data.schemas.Deferrability.NotDeferrable) {
			builder.space()._add("DEFERRABLE").space()._add(mode.getSqlValue());
		}
	}

	static boolean isNotValid(Constraint constraint) {
		String value = constraint.getSpecifics().get(NOT_VALID);
		if (value == null) return false;
		if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
			throw new IllegalArgumentException("Constraint notValid must be true or false.");
		}
		return Boolean.parseBoolean(value);
	}

	static void appendNotValid(Constraint constraint, AbstractSqlBuilder<?> builder, boolean supported) {
		if (!isNotValid(constraint)) return;
		if (!supported) throw new IllegalArgumentException("NOT VALID is unsupported for this constraint on the target PostgreSQL version.");
		builder.space()._add("NOT VALID");
	}
	static void appendCascadeRules(com.sqlapp.data.schemas.ForeignKeyConstraint constraint, AbstractSqlBuilder<?> builder) {
		if (constraint.getDeleteRule() != null) {
			builder.space().on().space().delete().space()._add(cascadeRule(constraint.getDeleteRule()));
		}
		if (constraint.getUpdateRule() != null) {
			builder.space().on().space().update().space()._add(cascadeRule(constraint.getUpdateRule()));
		}
	}

	private static String cascadeRule(com.sqlapp.data.schemas.CascadeRule rule) {
		return rule == com.sqlapp.data.schemas.CascadeRule.None ? "NO ACTION" : rule.getSqlValue();
	}

}
