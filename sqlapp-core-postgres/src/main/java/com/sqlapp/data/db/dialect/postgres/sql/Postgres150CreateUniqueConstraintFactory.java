package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.UniqueConstraint;

/** PostgreSQL 15 UNIQUE NULLS NOT DISTINCT constraints. */
public class Postgres150CreateUniqueConstraintFactory extends Postgres110CreateUniqueConstraintFactory {
	@Override
	protected void addOption(UniqueConstraint constraint, PostgresSqlBuilder builder) {
		PostgresConstraintOptions.appendNullsNotDistinct(constraint, builder);
	}
}
