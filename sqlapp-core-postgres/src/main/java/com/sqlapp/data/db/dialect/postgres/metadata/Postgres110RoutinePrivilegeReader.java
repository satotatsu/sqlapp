package com.sqlapp.data.db.dialect.postgres.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Procedure kind is available from PostgreSQL 11. */
public class Postgres110RoutinePrivilegeReader extends PostgresRoutinePrivilegeReader {
	protected Postgres110RoutinePrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo version) {
		return getSqlNodeCache().getString("routinePrivileges110.sql");
	}
}
