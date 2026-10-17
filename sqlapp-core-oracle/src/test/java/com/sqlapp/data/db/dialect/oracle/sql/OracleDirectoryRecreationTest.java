/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Directory;

class OracleDirectoryRecreationTest {
	@Test void directoryPathIsQuotedAsAStringAndDropRemainsValid() {
		Directory directory = new Directory("DATA_DIR").setDirectoryPath("/srv/owner's files");
		var registry = DialectHolder.oracle10gDialect.createSqlFactoryRegistry();
		assertEquals("CREATE OR REPLACE DIRECTORY DATA_DIR AS '/srv/owner''s files'", registry.createSql(directory, SqlType.CREATE).get(0).getSqlText());
		assertEquals("DROP DIRECTORY DATA_DIR", registry.createSql(directory, SqlType.DROP).get(0).getSqlText());
	}
	@Test void missingOrBlankDirectoryPathsAreRejected() {
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Directory("D"), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Directory("D").setDirectoryPath(" "), SqlType.CREATE));
	}
}
