/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;

final class SqlServerConstraintSupport {
	private SqlServerConstraintSupport() {
	}

	static boolean flag(Constraint constraint, String key) {
		String value = constraint.getSpecifics().get(key);
		if (value == null)
			return false;
		if ("true".equalsIgnoreCase(value) || "1".equals(value))
			return true;
		if ("false".equalsIgnoreCase(value) || "0".equals(value))
			return false;
		throw new IllegalArgumentException(key + " requires true, false, 1 or 0: " + value);
	}

	static boolean untrusted(Constraint constraint) {
		boolean untrusted = flag(constraint, "is_not_trusted");
		flag(constraint, "is_not_for_replication");
		if (!constraint.isEnable() && constraint.getSpecifics().containsKey("is_not_trusted") && !untrusted)
			throw new IllegalArgumentException(
					"Disabled constraints cannot be marked trusted: " + constraint.getName());
		return untrusted || !constraint.isEnable();
	}

	static void replication(Constraint constraint, SqlServerSqlBuilder builder) {
		untrusted(constraint);
		if (flag(constraint, "is_not_for_replication"))
			builder.space()._add("NOT FOR REPLICATION");
	}

	static void addPrefix(Constraint constraint, SqlServerSqlBuilder builder) {
		builder.space()._add(untrusted(constraint) ? "WITH NOCHECK" : "WITH CHECK");
	}

	static void columnCheck(com.sqlapp.data.schemas.Column column, SqlServerSqlBuilder builder) {
		var constraint = column.getCheckConstraint();
		if (constraint.getName() != null && !constraint.getName().isBlank())
			builder.constraint().space().name(constraint.getName());
		builder.check();
		replication(constraint, builder);
		builder.space()._add("(")._add(constraint.getExpression())._add(")");
	}

	private static Table table(Constraint constraint) {
		return constraint instanceof CheckConstraint check ? check.getTable()
				: ((ForeignKeyConstraint) constraint).getTable();
	}

	static boolean stateOnly(Constraint original, Constraint target) {
		Constraint normalized = target.clone();
		normalized.setEnable(original.isEnable());
		if (original.getSpecifics().containsKey("is_not_trusted"))
			normalized.getSpecifics().put("is_not_trusted", original.getSpecifics().get("is_not_trusted"));
		else
			normalized.getSpecifics().remove("is_not_trusted");
		return original.equals(normalized);
	}

	static void changeState(Constraint constraint, SqlServerSqlBuilder builder, boolean decorate,
			List<SqlOperation> result) {
		if (untrusted(constraint)) {
			restore(constraint, builder, decorate, true, result);
			return;
		}
		if (constraint.getName() == null || constraint.getName().isBlank())
			throw new IllegalArgumentException("A named constraint is required to validate existing data");
		builder.alter().table().name(table(constraint), decorate).space()._add("WITH CHECK CHECK CONSTRAINT ")
				.name(constraint.getName());
		result.add(new SqlOperation(builder.toString(), SqlType.ALTER, null, constraint));
	}

	static void restore(Constraint constraint, SqlServerSqlBuilder builder, boolean decorate, boolean inline,
			List<SqlOperation> result) {
		if (!untrusted(constraint))
			return;
		if (constraint.getName() == null || constraint.getName().isBlank())
			throw new IllegalArgumentException("A named constraint is required to restore disabled or untrusted state");
		if (inline || !constraint.isEnable()) {
			builder.alter().table().name(table(constraint), decorate).space()._add("NOCHECK CONSTRAINT ")
					.name(constraint.getName());
			result.add(new SqlOperation(builder.toString(), SqlType.ALTER, null, constraint));
		}
		if (inline && constraint.isEnable()) {
			builder = (SqlServerSqlBuilder) builder.getDialect().createSqlBuilder();
			builder.alter().table().name(table(constraint), decorate).space()._add("CHECK CONSTRAINT ")
					.name(constraint.getName());
			result.add(new SqlOperation(builder.toString(), SqlType.ALTER, null, constraint));
		}
	}
}
