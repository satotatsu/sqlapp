/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.bulk.MySqlBulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertProvider;

/** MariaDB provider for the MySQL-family staging executor. */
public class MariadbBulkUpsertProvider implements BulkUpsertProvider {
	@Override public boolean supports(final Dialect dialect){return dialect!=null&&"MariaDB".equalsIgnoreCase(dialect.getProductName());}
	@Override public BulkUpsertExecutor create(final Dialect dialect){return new MySqlBulkUpsertExecutor(dialect);}
}
