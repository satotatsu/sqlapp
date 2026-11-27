/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleSynonymRecreationTest {
	@Test
	void privateReplacementChangesTheTargetWithoutDroppingTheSynonym() {
		Synonym before = new Synonym("S").setSchemaName("APP").setObjectSchemaName("HR").setObjectName("EMP"),
				after = before.clone();
		after.setObjectSchemaName("OTHER").setObjectName("NEW_EMP").setDbLinkName("REMOTE");
		var registry = DialectHolder.oracle10gDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var operations = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
		assertEquals(1, operations.size());
		assertEquals(SqlType.ALTER, operations.get(0).getSqlType());
		assertEquals("CREATE OR REPLACE SYNONYM APP.S FOR OTHER.NEW_EMP@REMOTE", operations.get(0).getSqlText());
		assertNull(before.getDbLinkName());
	}

	@Test
	void publicReplacementMayRemoveTheRemoteLink() {
		PublicSynonym before = new PublicSynonym("S").setObjectSchemaName("HR").setObjectName("EMP")
				.setDbLinkName("REMOTE"), after = before.clone();
		after.setDbLinkName(null);
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		assertEquals("CREATE OR REPLACE PUBLIC SYNONYM S FOR HR.EMP",
				registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)).get(0).getSqlText());
	}

	@Test
	void replacementRejectsIdentityAndUnmodeledChanges() {
		Synonym before = new Synonym("S").setObjectName("EMP"), after = before.clone();
		after.setSchemaName("OTHER");
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
		PublicSynonym original = new PublicSynonym("S").setObjectName("EMP"), renamed = original.clone();
		renamed.setName("NEW_NAME");
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(renamed, SqlType.ALTER).createDiffSql(original.diff(renamed)));
	}

	@Test
	void privateAndPublicDropFactoriesRemainAvailable() {
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		assertEquals("DROP SYNONYM S", registry.createSql(new Synonym("S"), SqlType.DROP).get(0).getSqlText());
		assertEquals("DROP PUBLIC SYNONYM S",
				registry.createSql(new PublicSynonym("S"), SqlType.DROP).get(0).getSqlText());
	}

	@Test
	void modifiedStateResolvesReplacementAndUnchangedDiffProducesNothing() {
		Synonym before = new Synonym("S").setObjectName("EMP"), after = before.clone();
		after.setDbLinkName("REMOTE");
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var difference = before.diff(after);
		assertTrue(registry.getSqlFactory(difference).createDiffSql(difference).get(0).getSqlText()
				.contains("EMP@REMOTE"));
		assertTrue(registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(after.diff(after.clone())).isEmpty());
		PublicSynonym publicSynonym = new PublicSynonym("S").setObjectName("EMP");
		assertTrue(registry.getSqlFactory(publicSynonym, SqlType.ALTER)
				.createDiffSql(publicSynonym.diff(publicSynonym.clone())).isEmpty());
	}

	@Test
	void privateRemoteSynonymKeepsItsLinkAcrossXml() throws Exception {
		Synonym synonym = new Synonym("S").setSchemaName("APP").setObjectSchemaName("HR").setObjectName("EMP")
				.setDbLinkName("remote.us.example.com");
		Synonym restored = SchemaUtils.readXml(new java.io.StringReader(synonym.asXml()));
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.oracle10gDialect,
				DialectHolder.oracle26aiDialect)) {
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			assertEquals("CREATE OR REPLACE SYNONYM APP.S FOR HR.EMP@remote.us.example.com",
					registry.createSql(restored, SqlType.CREATE).get(0).getSqlText());
		}
	}

	@Test
	void publicRemoteSynonymKeepsItsLink() {
		PublicSynonym synonym = new PublicSynonym("S").setObjectSchemaName("HR").setObjectName("EMP")
				.setDbLinkName("REMOTE");
		String sql = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(synonym, SqlType.CREATE)
				.get(0).getSqlText();
		assertEquals("CREATE OR REPLACE PUBLIC SYNONYM S FOR HR.EMP@REMOTE", sql);
	}

	@Test
	void ownerMayBeOmittedAndLocalTargetsRemainLocal() {
		Synonym synonym = new Synonym("S").setObjectName("EMP");
		assertEquals("CREATE OR REPLACE SYNONYM S FOR EMP", DialectHolder.oracle10gDialect.createSqlFactoryRegistry()
				.createSql(synonym, SqlType.CREATE).get(0).getSqlText());
		synonym.setDbLinkName("REMOTE");
		assertTrue(DialectHolder.oracle10gDialect.createSqlFactoryRegistry().createSql(synonym, SqlType.CREATE).get(0)
				.getSqlText().endsWith("EMP@REMOTE"));
	}

	@Test
	void quotingAppliesToObjectNamesAndNotTheDatabaseLinkDomain() {
		Synonym synonym = new Synonym("local name").setObjectSchemaName("remote owner").setObjectName("remote object")
				.setDbLinkName("remote.example.com");
		String sql = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(synonym, SqlType.CREATE)
				.get(0).getSqlText();
		assertTrue(sql.contains("\"remote owner\".\"remote object\"@remote.example.com"), sql);
	}

	@Test
	void invalidLinksAndMissingTargetsFailExplicitly() {
		for (String link : List.of("", "remote link", "remote;DROP TABLE T", "remote..example", "\"REMOTE\"")) {
			Synonym synonym = new Synonym("S").setObjectName("EMP").setDbLinkName(link);
			assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle26aiDialect
					.createSqlFactoryRegistry().createSql(synonym, SqlType.CREATE));
		}
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
				.createSql(new PublicSynonym("S"), SqlType.CREATE));
	}
}
