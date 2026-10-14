/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;

/** PostgreSQL 18 pg_logicalinspect SQL; execution requires the extension and server-file privileges. */
public class PostgresLogicalInspectBuilder {
	private final Dialect dialect;

	public PostgresLogicalInspectBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public String snapshotMeta(String filename) {
		return snapshot("pg_get_logical_snapshot_meta", filename);
	}

	public String snapshotInfo(String filename) {
		return snapshot("pg_get_logical_snapshot_info", filename);
	}

	private String snapshot(String function, String filename) {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException("pg_logicalinspect requires PostgreSQL 18 or later.");
		}
		if (filename == null || filename.isBlank() || filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0
				|| filename.indexOf('\0') >= 0 || filename.equals(".") || filename.equals("..")) {
			throw new IllegalArgumentException("filename must be a snapshot file name without a directory.");
		}
		return "SELECT * FROM " + function + "('" + filename.replace("'", "''") + "')";
	}
}
