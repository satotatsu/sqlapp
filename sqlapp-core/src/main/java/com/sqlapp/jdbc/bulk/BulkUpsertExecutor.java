/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.schemas.Table;

/** Executes a set-oriented upsert using a bulk-loaded staging table. */
@FunctionalInterface
public interface BulkUpsertExecutor {
	/** Stages all rows, applies them to the target and returns affected rows. */
	long execute(Connection connection, Table table, BulkUpsertOption options)
			throws SQLException;
}
