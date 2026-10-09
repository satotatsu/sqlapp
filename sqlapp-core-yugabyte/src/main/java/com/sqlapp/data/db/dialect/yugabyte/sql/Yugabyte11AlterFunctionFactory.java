/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.sql;

import com.sqlapp.data.db.dialect.postgres.sql.PostgresAlterFunctionFactory;

/** PostgreSQL 11-based YSQL does not implement ALTER PROCEDURE. */
public class Yugabyte11AlterFunctionFactory extends PostgresAlterFunctionFactory {
	@Override
	protected boolean supportsProcedureSecurityAlter() { return false; }
}
