package com.sqlapp.data.db.dialect.mariadb;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.MySqlDefaultExpressionSupport;

/**
 * MariaDB default expressions: the MySQL forms, with expression defaults from
 * MariaDB 10.2.
 */
public class MariadbDefaultExpressionSupport extends MySqlDefaultExpressionSupport {

	public MariadbDefaultExpressionSupport(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected boolean supportsExpressionDefaults() {
		return getDialect() instanceof Mariadb10_20;
	}
}
