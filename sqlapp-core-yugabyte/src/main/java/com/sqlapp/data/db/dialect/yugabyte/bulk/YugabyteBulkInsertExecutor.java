/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.bulk.PostgresBulkInsertExecutor;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertTransaction;

/** Reuses PostgreSQL COPY while preventing YSQL auto-commit COPY batch commits. */
public class YugabyteBulkInsertExecutor extends PostgresBulkInsertExecutor {
	public YugabyteBulkInsertExecutor(Dialect dialect) {
		super(dialect);
	}

	@Override
	public long execute(Connection connection, Table table, BulkOption options) throws SQLException {
		try (var transaction = BulkUpsertTransaction.begin(connection, true)) {
			try {
				long affected = super.execute(connection, table, options);
				transaction.commit();
				return affected;
			} catch (SQLException | RuntimeException | Error e) {
				transaction.rollback(e);
				throw e;
			}
		}
	}
}
