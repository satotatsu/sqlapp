/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.bulk;

import java.io.InputStream;
import java.sql.SQLException;
import java.sql.Statement;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.bulk.MySqlBulkInsertExecutor;

/** MariaDB LOAD DATA uses the same encoding and validation as MySQL. */
public class MariadbBulkInsertExecutor extends MySqlBulkInsertExecutor {
	public MariadbBulkInsertExecutor(final Dialect dialect) { super(dialect); }

	/**
	 * MariaDB has no JSON cast; a subquery returning two rows raises an error (on MySQL it is only a warning).
	 * UNION ALL keeps both rows even when they are equal.
	 */
	@Override
	protected String abortExpression(final String variable) {
		return "(SELECT 1 UNION ALL SELECT " + variable + ")";
	}

	@Override
	protected void bindInput(Statement statement, InputStream input) throws SQLException {
		statement.unwrap(org.mariadb.jdbc.Statement.class).setLocalInfileInputStream(input);
	}
}
