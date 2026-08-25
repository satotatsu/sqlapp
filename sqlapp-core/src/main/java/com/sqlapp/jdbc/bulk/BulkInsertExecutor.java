/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.schemas.Table;

/** Executes a vendor-optimized bulk insert from a Schema table's rows. */
@FunctionalInterface
public interface BulkInsertExecutor {
	/** Writes all rows and returns the number supplied to the driver. */
	long execute(Connection connection, Table table, BulkOption options)
			throws SQLException;
}
