/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.schemas.Constraint;

final class FirebirdConstraintSql {
	private FirebirdConstraintSql() {
	}

	static void validate(Constraint constraint) {
		if (!(constraint instanceof com.sqlapp.data.schemas.UniqueConstraint
				|| constraint instanceof com.sqlapp.data.schemas.ForeignKeyConstraint
				|| constraint instanceof com.sqlapp.data.schemas.CheckConstraint))
			throw new IllegalArgumentException(
					"Firebird supports primary, unique, foreign key and check constraints only");
		if (!constraint.isEnable())
			throw new IllegalArgumentException("Firebird constraints cannot be disabled");
		if (constraint.getDeferrability() != null
				&& constraint.getDeferrability() != com.sqlapp.data.schemas.Deferrability.NotDeferrable)
			throw new IllegalArgumentException("Firebird constraints cannot be deferred");
		if (constraint instanceof com.sqlapp.data.schemas.CheckConstraint check
				&& (check.getExpression() == null || check.getExpression().isBlank()))
			throw new IllegalArgumentException("Firebird CHECK requires a non-empty expression");
	}

	static void validateTable(com.sqlapp.data.schemas.Table table) {
		for (var constraint : table.getConstraints())
			validate(constraint);
		for (var column : table.getColumns())
			if (column.getCheckConstraint() != null)
				validate(column.getCheckConstraint());
	}

	static void index(Constraint constraint, boolean descending, FirebirdSqlBuilder builder) {
		validate(constraint);
		String name = constraint.getSpecifics().get("firebird.indexName");
		if (name == null && !descending)
			return;
		if (name == null)
			name = constraint.getName();
		FirebirdPrivilege.identifier(name, "constraint index name");
		builder._add(descending ? " USING DESCENDING INDEX " : " USING ASCENDING INDEX ").name(name);
	}
}
