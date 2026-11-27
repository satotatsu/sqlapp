/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Synonym;

class Db2PublicAliasSqlTest {
	@Test
	void allPublicAliasKindsRetainPublicKeywordAndTargetKind() {
		for (String kind : java.util.List.of("MODULE", "SEQUENCE", "TABLE")) {
			var alias = new Synonym("A.Name").setSchemaName("SYSPUBLIC");
			alias.setObjectSchemaName("APP");
			alias.setObjectName("BASE");
			alias.getSpecifics().put("ALIAS_TYPE", kind);
			var registry = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			SqlFactory<Synonym> create = registry.getSqlFactory(alias, SqlType.CREATE),
					drop = registry.getSqlFactory(alias, SqlType.DROP);
			String sql = create.createSql(alias).getFirst().getSqlText();
			assertTrue(sql.startsWith("CREATE PUBLIC ALIAS"), sql);
			assertTrue(sql.contains("FOR " + kind), sql);
			assertTrue(drop.createSql(alias).getFirst().getSqlText().startsWith("DROP PUBLIC ALIAS"));
			SqlFactory<Synonym> old = DialectHolder.Db2_950Dialect.createSqlFactoryRegistry().getSqlFactory(alias,
					SqlType.CREATE);
			assertThrows(IllegalArgumentException.class, () -> old.createSql(alias));
		}
	}
}
