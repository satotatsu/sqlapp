/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.UniqueConstraint;

/** PostgreSQL 9.0 introduced deferrable PRIMARY KEY and UNIQUE constraints. */
public class Postgres90CreateUniqueConstraintFactory extends PostgresCreateUniqueConstraintFactory {
	@Override
	protected void addDeferrability(UniqueConstraint constraint, PostgresSqlBuilder builder) {
		PostgresConstraintOptions.appendDeferrability(constraint, builder);
	}
}
