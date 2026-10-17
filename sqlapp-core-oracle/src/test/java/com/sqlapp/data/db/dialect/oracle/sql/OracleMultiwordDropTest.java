/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.PackageBody;
import com.sqlapp.data.schemas.TypeBody;

class OracleMultiwordDropTest {
	@Test
	void packageAndTypeBodiesUseTheirRegisteredOracleFactories() {
		for (var dialect : java.util.List.of(DialectHolder.defaultDialect, DialectHolder.oracle10gDialect,
				DialectHolder.oracle26aiDialect)) {
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			assertEquals("DROP PACKAGE BODY APP.P",
					registry.createSql(new PackageBody("P").setSchemaName("APP"), SqlType.DROP).get(0).getSqlText());
			assertEquals("DROP TYPE BODY APP.T",
					registry.createSql(new TypeBody("T").setSchemaName("APP"), SqlType.DROP).get(0).getSqlText());
		}
	}
}
