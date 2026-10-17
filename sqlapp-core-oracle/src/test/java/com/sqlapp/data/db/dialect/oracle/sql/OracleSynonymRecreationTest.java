/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleSynonymRecreationTest {
	@Test void privateRemoteSynonymKeepsItsLinkAcrossXml() throws Exception {
		Synonym synonym = new Synonym("S").setSchemaName("APP").setObjectSchemaName("HR").setObjectName("EMP").setDbLinkName("remote.us.example.com");
		Synonym restored = SchemaUtils.readXml(new java.io.StringReader(synonym.asXml()));
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.oracle10gDialect, DialectHolder.oracle26aiDialect)) {
			var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
			assertEquals("CREATE OR REPLACE SYNONYM APP.S FOR HR.EMP@remote.us.example.com", registry.createSql(restored, SqlType.CREATE).get(0).getSqlText());
		}
	}
	@Test void publicRemoteSynonymKeepsItsLink() {
		PublicSynonym synonym = new PublicSynonym("S").setObjectSchemaName("HR").setObjectName("EMP").setDbLinkName("REMOTE");
		String sql = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(synonym, SqlType.CREATE).get(0).getSqlText();
		assertEquals("CREATE OR REPLACE PUBLIC SYNONYM S FOR HR.EMP@REMOTE", sql);
	}
	@Test void ownerMayBeOmittedAndLocalTargetsRemainLocal() {
		Synonym synonym = new Synonym("S").setObjectName("EMP");
		assertEquals("CREATE OR REPLACE SYNONYM S FOR EMP", DialectHolder.oracle10gDialect.createSqlFactoryRegistry().createSql(synonym, SqlType.CREATE).get(0).getSqlText());
		synonym.setDbLinkName("REMOTE");
		assertTrue(DialectHolder.oracle10gDialect.createSqlFactoryRegistry().createSql(synonym, SqlType.CREATE).get(0).getSqlText().endsWith("EMP@REMOTE"));
	}
	@Test void quotingAppliesToObjectNamesAndNotTheDatabaseLinkDomain() {
		Synonym synonym = new Synonym("local name").setObjectSchemaName("remote owner").setObjectName("remote object").setDbLinkName("remote.example.com");
		String sql = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(synonym, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("\"remote owner\".\"remote object\"@remote.example.com"), sql);
	}
	@Test void invalidLinksAndMissingTargetsFailExplicitly() {
		for (String link : List.of("", "remote link", "remote;DROP TABLE T", "remote..example", "\"REMOTE\"")) {
			Synonym synonym = new Synonym("S").setObjectName("EMP").setDbLinkName(link);
			assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(synonym, SqlType.CREATE));
		}
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(new PublicSynonym("S"), SqlType.CREATE));
	}
}
