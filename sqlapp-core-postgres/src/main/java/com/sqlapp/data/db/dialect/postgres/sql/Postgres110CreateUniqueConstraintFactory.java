package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.UniqueConstraint;

/** PostgreSQL 11 covering PRIMARY KEY and UNIQUE constraints. */
public class Postgres110CreateUniqueConstraintFactory extends Postgres90CreateUniqueConstraintFactory {
	@Override
	protected void addDeferrability(UniqueConstraint constraint, PostgresSqlBuilder builder) {
		PostgresConstraintOptions.appendIncludes(constraint, builder);
		super.addDeferrability(constraint, builder);
	}
}
