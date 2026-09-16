/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.bulk;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.mariadb.DialectHolder;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotResolver;
import com.sqlapp.data.db.dialect.mysql.bulk.MySqlSetBasedMigrationSnapshotExecutor;

class MariadbBulkInsertProviderTest {
	@Test
	void resolvesMariaDbProviderAheadOfInheritedMySqlProvider() {
		assertInstanceOf(MariadbBulkInsertExecutor.class,
				BulkInsertResolver.resolve(DialectHolder.mariadb11_80Dialect));
	}

	@Test
	void reusesMySqlSetBasedSnapshotProvider() {
		assertInstanceOf(MySqlSetBasedMigrationSnapshotExecutor.class,
				SetBasedMigrationSnapshotResolver.resolve(DialectHolder.mariadb11_80Dialect));
	}
}
