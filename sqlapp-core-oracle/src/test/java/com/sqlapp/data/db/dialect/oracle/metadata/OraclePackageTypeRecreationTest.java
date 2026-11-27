/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Package;
import com.sqlapp.data.schemas.PackageBody;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.data.schemas.TypeBody;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.DefinitionProperty;

class OraclePackageTypeRecreationTest {
	private List<AbstractSchemaObject<?>> objects() {
		return List.of(new Package("P"), new PackageBody("P"), new Type("T"), new TypeBody("T"));
	}

	private String source(AbstractSchemaObject<?> obj) {
		if (obj instanceof Package)
			return "PACKAGE P AUTHID CURRENT_USER\nAS PROCEDURE run(p NUMBER DEFAULT 42); END P;";
		if (obj instanceof PackageBody)
			return "PACKAGE BODY P\nAS PROCEDURE run(p NUMBER DEFAULT 42) IS BEGIN NULL; END run;\nBEGIN NULL; END P;";
		if (obj instanceof Type)
			return "TYPE T AUTHID CURRENT_USER AS OBJECT (n NUMBER, MEMBER FUNCTION value RETURN NUMBER) NOT FINAL";
		return "TYPE BODY T AS MEMBER FUNCTION value RETURN NUMBER IS BEGIN RETURN SELF.n; END value; END T;";
	}

	@Test
	void completeSourceAndLegacyDefinitionsSurviveAcrossVersionsAndXml() throws Exception {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.oracle11gR2Dialect,
				DialectHolder.oracle19cDialect, DialectHolder.oracle21cDialect, DialectHolder.oracle23aiDialect,
				DialectHolder.oracle26aiDialect)) {
			for (var obj : objects()) {
				String source = source(obj);
				String ddl = OracleMetadataUtils.getSourceDefinition(obj, List.of(source.split("\n", -1)));
				assertEquals("CREATE OR REPLACE " + source, ddl);
				((DefinitionProperty<?>) obj).setDefinition(ddl);
				assertEquals(ddl,
						dialect.createSqlFactoryRegistry().createSql(obj, SqlType.CREATE).get(0).getSqlText());
				AbstractSchemaObject<?> restored = SchemaUtils.readXml(new java.io.StringReader(obj.asXml()));
				assertEquals(ddl,
						dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE).get(0).getSqlText());
				((DefinitionProperty<?>) obj).setDefinition(source);
				assertEquals(ddl,
						dialect.createSqlFactoryRegistry().createSql(obj, SqlType.CREATE).get(0).getSqlText());
			}
		}
	}

	@Test
	void unavailableSourceFailsWithObjectIdentity() {
		for (var obj : objects()) {
			obj.setSchemaName("APP");
			for (List<String> source : List.<List<String>>of(List.of(), List.of(" "))) {
				assertTrue(assertThrows(IllegalStateException.class,
						() -> OracleMetadataUtils.getSourceDefinition(obj, source)).getMessage()
						.contains("APP." + obj.getName()));
			}
			assertThrows(IllegalStateException.class, () -> OracleMetadataUtils.getSourceDefinition(obj, null));
		}
	}
}
