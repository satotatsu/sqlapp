/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

public class Postgres92CreateCheckConstraintFactory extends PostgresCreateCheckConstraintFactory implements PostgresConstraintOptions.NotValidFactory {
	@Override
	protected boolean supportsNotValid() { return true; }
}
