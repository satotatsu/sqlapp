/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.i;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.db2.i.Db2I75Test.*;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Synonym;

class Db2I75AliasTest {
	private static final boolean ZOS = false;

	@Test
	void conflictingAliasNamespacesCannotSilentlyLoseAnObject() {
		var error = assertThrows(RuntimeException.class, () -> read("collision"));
		assertTrue(error.toString().contains("Shared Synonym model"), error.toString());
	}

	@Test
	void dropAddressesTheAliasAndKeepsTheTargetKind() {
		var dialect = new Db2I75();
		var alias = read("normal");
		String text = dialect.createSqlFactoryRegistry().createSql(alias, SqlType.DROP).get(0).getSqlText();
		assertTrue(text.contains("DROP ALIAS \"App\".\"Alias\""), text);
		assertEquals(ZOS, text.contains("FOR TABLE"), text);
		assertFalse(text.contains("Target"), text);
		alias.getSpecifics().put("ALIAS_TYPE", "SEQUENCE");
		if (ZOS)
			assertTrue(dialect.createSqlFactoryRegistry().createSql(alias, SqlType.DROP).get(0).getSqlText()
					.contains("FOR SEQUENCE"));
		else
			assertThrows(IllegalArgumentException.class,
					() -> dialect.createSqlFactoryRegistry().createSql(alias, SqlType.DROP));
		alias.getSpecifics().put("DB2_HOST_NAME_KIND", "SYNONYM");
		assertThrows(IllegalArgumentException.class,
				() -> dialect.createSqlFactoryRegistry().createSql(alias, SqlType.DROP));
	}

	@Test
	void readsMultipleDirectTargetsInOneFilteredQueryAndSurvivesXml() throws Exception {
		var calls = new AtomicInteger();
		var bindings = new ArrayList<Object>();
		var reader = new Db2I75().getCatalogReader().getSchemaReader().getSynonymReader();
		reader.setSchemaName("App");
		reader.setObjectName("Alias");
		var aliases = reader.getAllFull(connection(calls, bindings, "normal"));
		assertEquals(1, calls.get());
		assertEquals(List.of("App", "Alias"), bindings);
		assertEquals(2, aliases.size());
		Synonym alias = SchemaUtils.readXml(new StringReader(aliases.get(0).asXml()));
		assertEquals("Base", alias.getObjectSchemaName());
		assertEquals("Target\"name", alias.getObjectName());
		assertEquals("ALIAS", alias.getSpecifics().get("DB2_HOST_NAME_KIND"));
		assertEquals("remark  ", alias.getRemarks());
		assertEquals(Timestamp.valueOf("2020-01-02 03:04:05"), alias.getLastAlteredAt());
		String text = create(alias);
		assertTrue(text.contains("ALIAS \"App\".\"Alias\""), text);
		assertTrue(text.contains("FOR \"Base\".\"Target\"\"name\""), text);
		assertFalse(text.contains("FOR TABLE"), text);
	}

	@Test
	void retainsRemoteLocationAndQuotesEachPart() throws Exception {
		Synonym alias = read("remote");
		assertEquals("Remote\"db", alias.getDbLinkName());
		alias = SchemaUtils.readXml(new StringReader(alias.asXml()));
		String text = create(alias);
		assertTrue(text.contains("FOR \"Remote\"\"db\".\"Base\".\"Target\"\"name\""), text);
	}

	@Test
	void distinguishesSequenceAliasesFromTables() {
		Synonym alias = read("sequence");
		if (ZOS) {
			assertTrue(create(alias).contains("FOR SEQUENCE \"Base\"."));
			alias.setDbLinkName("REMOTE");
		}
		assertThrows(IllegalArgumentException.class, () -> create(alias));
	}

	@Test
	void legacySynonymsAreNotSilentlyConvertedToAliases() {
		Synonym alias = read("legacy");
		assertEquals("SYNONYM", alias.getSpecifics().get("DB2_HOST_NAME_KIND"));
		assertThrows(IllegalArgumentException.class, () -> create(alias));
		alias.setDefinition("CREATE ALIAS APP.A FOR BASE.T");
		assertThrows(IllegalArgumentException.class, () -> create(alias));
	}

	@Test
	void memberAliasesPreserveScopeAndRejectUnverifiedSpecialValues() throws Exception {
		Synonym alias = read("member");
		alias = SchemaUtils.readXml(new StringReader(alias.asXml()));
		if (ZOS) {
			Synonym invalid = alias;
			assertThrows(IllegalArgumentException.class, () -> create(invalid));
		} else {
			String text = create(alias);
			assertTrue(text.matches("(?s).*\\(\\s*\"JAN\"\"data\"\\s*\\).*"), text);
		}
		alias.getSpecifics().put("DB2_HOST_MEMBER_NAME", "*LAST");
		Synonym special = alias;
		assertThrows(IllegalArgumentException.class, () -> create(special));
		alias.setDbLinkName("REMOTE");
		alias.getSpecifics().put("DB2_HOST_MEMBER_NAME", "JAN");
		assertThrows(IllegalArgumentException.class, () -> create(special));
	}

	@Test
	void missingTargetAndUnknownKindsFailWithoutGuessing() {
		assertThrows(RuntimeException.class, () -> read("missing"));
		Synonym alias = read("normal");
		alias.getSpecifics().put("ALIAS_TYPE", "MODULE");
		assertThrows(IllegalArgumentException.class, () -> create(alias));
		alias.getSpecifics().put("ALIAS_TYPE", "TABLE");
		alias.setObjectSchemaName(null);
		assertThrows(IllegalArgumentException.class, () -> create(alias));
	}

	@Test
	void explicitDefinitionIsReplayedButInvalidStatementsFail() {
		Synonym alias = read("normal");
		String definition = "CREATE ALIAS APP.A FOR BASE.T";
		alias.setDefinition(definition);
		assertEquals(definition, create(alias));
		alias.setDefinition("ALTER ALIAS APP.A FOR BASE.T");
		assertThrows(IllegalArgumentException.class, () -> create(alias));
	}

	private static Synonym read(String mode) {
		return new Db2I75().getCatalogReader().getSchemaReader().getSynonymReader()
				.getAllFull(connection(new AtomicInteger(), new ArrayList<>(), mode)).get(0);
	}

	private static String create(Synonym synonym) {
		return new Db2I75().createSqlFactoryRegistry().createSql(synonym, SqlType.CREATE).get(0).getSqlText();
	}

	private static Connection connection(AtomicInteger calls, List<Object> bindings, String mode) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> metadata("DB2", ZOS ? "DSN13015" : "QSQ07050");
		case "prepareStatement" -> {
			String query = (String) a[0];
			assertTrue(query.contains("host:synonyms"), query);
			assertFalse(query.contains("SYSPROC.BASE_TABLE"), "Keep direct references, not resolved chains");
			yield proxy(PreparedStatement.class, (sp, sm, sa) -> {
				if (sm.getName().equals("setObject") || sm.getName().equals("setString"))
					bindings.add(sa[1]);
				if (!sm.getName().equals("executeQuery"))
					return fallback(sm.getReturnType());
				calls.incrementAndGet();
				var first = row("SCHEMA_NAME", "App  ", "SYNONYM_NAME", "Alias  ", "TARGET_SCHEMA", "Base  ",
						"TARGET_NAME", mode.equals("missing") ? null : "Target\"name  ", "TARGET_LOCATION",
						mode.equals("remote") ? "Remote\"db  " : null, "ALIAS_TYPE",
						mode.equals("sequence") ? "SEQUENCE" : "TABLE", "NAME_KIND",
						mode.equals("legacy") ? "SYNONYM" : "ALIAS", "MEMBER_NAME",
						mode.equals("member") ? "JAN\"data" : ZOS ? null : "*FIRST", "BASE_CATALOG",
						ZOS ? null : "LOCALDB", "CREATED_AT", null, "ALTERED_AT",
						Timestamp.valueOf("2020-01-02 03:04:05"), "REMARKS", "remark  ");
				var second = new java.util.LinkedHashMap<>(first);
				second.put("SYNONYM_NAME", "Alias2");
				if (mode.equals("collision")) {
					second.put("SYNONYM_NAME", "Alias");
					second.put("ALIAS_TYPE", "SEQUENCE");
				}
				return rows(List.of(first, second));
			});
		}
		default -> fallback(m.getReturnType());
		});
	}
}
