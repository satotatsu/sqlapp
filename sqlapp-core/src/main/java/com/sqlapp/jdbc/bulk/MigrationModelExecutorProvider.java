/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import com.sqlapp.data.db.dialect.Dialect;

/** SPI for dialect-owned model materialization. */
public interface MigrationModelExecutorProvider {
	boolean supports(Dialect dialect);

	MigrationModelExecutor create(Dialect dialect);
}
