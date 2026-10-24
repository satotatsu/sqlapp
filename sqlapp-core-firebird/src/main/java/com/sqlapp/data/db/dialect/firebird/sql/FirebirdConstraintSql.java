/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.schemas.Constraint;

final class FirebirdConstraintSql {
	private FirebirdConstraintSql() { }
	static void index(Constraint constraint, boolean descending, FirebirdSqlBuilder builder) {
		if (!constraint.isEnable()) throw new IllegalArgumentException("Firebird constraints cannot be disabled");
		if (constraint.getDeferrability() != null && constraint.getDeferrability() != com.sqlapp.data.schemas.Deferrability.NotDeferrable)
			throw new IllegalArgumentException("Firebird constraints cannot be deferred");
		String name = constraint.getSpecifics().get("firebird.indexName");
		if (name == null && !descending) return;
		if (name == null) name = constraint.getName();
		FirebirdPrivilege.identifier(name, "constraint index name");
		builder._add(descending ? " USING DESCENDING INDEX " : " USING ASCENDING INDEX ").name(name);
	}
}
