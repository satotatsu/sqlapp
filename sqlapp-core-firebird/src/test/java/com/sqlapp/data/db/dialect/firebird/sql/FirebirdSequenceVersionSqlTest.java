/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

class FirebirdSequenceVersionSqlTest {
	@Test
	void isolatesSequenceOptionsToVersionThreeAndLater() {
		var sequence = new Sequence("audit_seq").setStartValue(50).setIncrementBy(5);
		var legacy = DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(sequence, SqlType.CREATE)
				.get(0).getSqlText();
		assertFalse(legacy.contains("START WITH"));
		assertFalse(legacy.contains("INCREMENT BY"));
		for (var dialect : new com.sqlapp.data.db.dialect.Dialect[] { DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect50 }) {
			var registry = dialect.createSqlFactoryRegistry();
			var sql = registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("START WITH 50"), sql);
			assertTrue(sql.contains("INCREMENT BY 5"), sql);
			assertInstanceOf(Firebird30CreatePackageFactory.class,
					registry.getSqlFactory(new com.sqlapp.data.schemas.Package("audit_package"), SqlType.CREATE));
		}
	}
}
