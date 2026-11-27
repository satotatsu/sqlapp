/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateCheckConstraintFactory;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Table;

public class FirebirdCreateCheckConstraintFactory extends AbstractCreateCheckConstraintFactory<FirebirdSqlBuilder> {
	@Override
	public void addCreateObject(CheckConstraint constraint, FirebirdSqlBuilder builder) {
		FirebirdConstraintSql.validate(constraint);
		if (constraint.getTable() == null)
			throw new IllegalArgumentException("Firebird CHECK requires an owning table");
		super.addCreateObject(constraint, builder);
	}

	@Override
	public void addObjectDetail(CheckConstraint constraint, Table table, FirebirdSqlBuilder builder) {
		FirebirdConstraintSql.validate(constraint);
		super.addObjectDetail(constraint, table, builder);
	}
}
