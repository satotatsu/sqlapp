/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

/** Selects the INSERT transport without changing row/column semantics. */
public enum BulkInsertMethod {
	AUTO, NATIVE, JDBC_BATCH
}
