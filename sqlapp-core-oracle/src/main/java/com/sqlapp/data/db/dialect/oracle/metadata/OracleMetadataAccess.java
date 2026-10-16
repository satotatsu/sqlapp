/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** DBMS_METADATA hides inaccessible objects behind ORA-31603. */
final class OracleMetadataAccess {
	private OracleMetadataAccess() { }
	static boolean isDenied(Throwable error) {
		return isDenied(error, Collections.newSetFromMap(new IdentityHashMap<>()));
	}
	private static boolean isDenied(Throwable error, Set<Throwable> seen) {
		if (error == null || !seen.add(error)) return false;
		if (error instanceof SQLException sql) {
			if (sql.getErrorCode() == 1031 || sql.getErrorCode() == 31603) return true;
			if (isDenied(sql.getNextException(), seen)) return true;
		}
		return isDenied(error.getCause(), seen);
	}
}
