/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.Db2Federation;
import com.sqlapp.data.db.dialect.db2.Db2Federation.*;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;

class Db2FederationSqlTest {
	private Wrapper wrapper(Map<String, String> options, String remarks) {
		return new Wrapper("W.with.dot", "lib's.so", false, options, remarks, null, Map.of());
	}

	private Server server(Map<String, String> options, String remarks) {
		return new Server("S", "W.with.dot", "ORACLE", "9i", options, Map.of("DATE_COMPAT", "Y"), remarks, null);
	}

	private List<String> alter(Catalog before, Catalog after) {
		SqlFactory<Catalog> factory = DialectHolder.Db2_1215Dialect.createSqlFactoryRegistry().getSqlFactory(after,
				SqlType.ALTER);
		return factory.createDiffSql(before.diff(after)).stream().map(SqlOperation::getSqlText).toList();
	}

	@Test
	void catalogCreatesWrappersAndCategoryDefaultsBeforeServersAndSchemas() {
		Catalog catalog = new Catalog();
		catalog.getSchemas().add(new Schema("APP"));
		Db2Federation.set(catalog, List.of(wrapper(Map.of("MODULE", " path's "), "wrapper's comment")),
				List.of(server(Map.of("NODE", "service"), "server's comment")),
				List.of(new Defaults("W.with.dot", "ORACLE", "9i", Map.of("CPU_RATIO", "2.0"))));
		var registry = DialectHolder.Db2_1215Dialect.createSqlFactoryRegistry();
		registry.getOptions().setCreateIfNotExists(false);
		SqlFactory<Catalog> factory = registry.getSqlFactory(catalog, SqlType.CREATE);
		var sql = factory.createSql(catalog).stream().map(SqlOperation::getSqlText).toList();
		assertEquals("CREATE WRAPPER \"W.with.dot\" LIBRARY 'lib''s.so' OPTIONS (MODULE ' path''s ')", sql.getFirst());
		assertTrue(sql.get(2).startsWith("ALTER SERVER TYPE ORACLE VERSION '9i' WRAPPER \"W.with.dot\""));
		assertEquals("CREATE SERVER \"S\" TYPE ORACLE VERSION '9i' WRAPPER \"W.with.dot\" OPTIONS (NODE 'service')",
				sql.get(3));
		assertFalse(sql.stream().anyMatch(s -> s.contains("DATE_COMPAT")));
		assertTrue(sql.getLast().contains("CREATE SCHEMA APP"));
	}

	@Test
	void authRequirementsAndCompleteDefinitionPrecedenceRemainExplicit() {
		Catalog catalog = new Catalog();
		var missingAuth = new Server("S", "DRDA", "DB2/LUW", "11.5", Map.of("DBNAME", "sample"), Map.of(), null, null);
		Db2Federation.set(catalog, List.of(), List.of(missingAuth), List.of());
		assertThrows(IllegalArgumentException.class,
				() -> Db2FederationSql.create(catalog, DialectHolder.Db2_1215Dialect));
		String ddl = "CREATE SERVER S TYPE DB2/LUW VERSION 11 WRAPPER DRDA AUTHORIZATION \"example_user\" PASSWORD \"example_password\" OPTIONS (DBNAME 'sample')";
		var complete = new Server("S", null, null, null, null, null, null, ddl);
		Db2Federation.set(catalog, List.of(), List.of(complete), List.of());
		assertEquals(List.of(ddl), Db2FederationSql.create(catalog, DialectHolder.Db2_1155Dialect));
		var sso = new Server("S", "DRDA", "DB2/LUW", "12.1", Map.of("SSO_AUTH", "Y"), Map.of(), null, null);
		Db2Federation.set(catalog, List.of(), List.of(sso), List.of());
		assertTrue(Db2FederationSql.create(catalog, DialectHolder.Db2_1215Dialect).getFirst()
				.contains("OPTIONS (SSO_AUTH 'Y')"));
		assertThrows(IllegalArgumentException.class,
				() -> Db2FederationSql.create(catalog, DialectHolder.Db2_1155Dialect));
	}

	@Test
	void nativeAlterCombinesOptionChangesAndCommentsAndSupportsReverse() {
		Catalog before = new Catalog(), after = new Catalog();
		Db2Federation.set(before, List.of(wrapper(Map.of("KEEP", "1", "REMOVE", "old"), "old")),
				List.of(server(Map.of("NODE", "old"), "old")), List.of());
		Db2Federation.set(after, List.of(wrapper(Map.of("KEEP", "2", "NEW", "new"), null)),
				List.of(server(Map.of("NODE", "new"), null)), List.of());
		var sql = alter(before, after);
		assertEquals("ALTER WRAPPER \"W.with.dot\" OPTIONS (SET KEEP '2', ADD NEW 'new', DROP REMOVE)", sql.getFirst());
		assertTrue(sql.contains("COMMENT ON WRAPPER \"W.with.dot\" IS NULL"));
		assertTrue(sql.contains("ALTER SERVER \"S\" OPTIONS (SET NODE 'new')"));
		assertTrue(alter(after, before).contains("ALTER SERVER \"S\" OPTIONS (SET NODE 'old')"));
		assertFalse(sql.stream().anyMatch(s -> s.startsWith("DROP ")));
		assertEquals("old", Db2Federation.get(before).servers().getFirst().options().get("NODE"));
	}

	@Test
	void unchangedUnreplayableDefaultsAndDerivedOptionsDoNotGenerateAlter() {
		Catalog before = new Catalog(), after = new Catalog();
		var defaults = new Defaults("W", null, null, Map.of("CPU_RATIO", "2"));
		Db2Federation.set(before, List.of(), List.of(server(Map.of(), null)), List.of(defaults));
		var previous = Db2Federation.get(before).servers().getFirst();
		var changed = new Server(previous.name(), previous.wrapperName(), previous.type(), previous.version(),
				previous.options(), Map.of("DATE_COMPAT", "Y"), previous.remarks(), null);
		Db2Federation.set(after, List.of(), List.of(changed), List.of(defaults));
		assertTrue(alter(before, after).isEmpty());
	}

	@Test
	void categoryOptionsAndServerVersionsUseNativeAlter() {
		Catalog before = new Catalog(), after = new Catalog();
		var previous = server(Map.of(), null);
		Db2Federation.set(before, List.of(), List.of(previous),
				List.of(new Defaults("W", "ORACLE", null, Map.of("CPU_RATIO", "2"))));
		Db2Federation.set(after, List.of(),
				List.of(new Server(previous.name(), previous.wrapperName(), previous.type(), "10", previous.options(),
						previous.derivedOptions(), null, null)),
				List.of(new Defaults("W", "ORACLE", null, Map.of("CPU_RATIO", "3"))));
		var sql = alter(before, after);
		assertTrue(sql.contains("ALTER SERVER TYPE ORACLE WRAPPER \"W\" OPTIONS (SET CPU_RATIO '3')"));
		assertTrue(sql.contains("ALTER SERVER \"S\" VERSION '10'"));
	}

	@Test
	void destructiveReplacementAndAmbiguousNativeSyntaxAreRejected() {
		Catalog original = new Catalog(), target = new Catalog();
		Db2Federation.set(original, List.of(wrapper(Map.of(), null)), List.of(server(Map.of(), null)), List.of());
		assertThrows(IllegalArgumentException.class, () -> alter(original, target));
		Db2Federation.set(target, List.of(new Wrapper("W.with.dot", "new.so", false, Map.of(), null, null, Map.of())),
				List.of(server(Map.of(), null)), List.of());
		assertThrows(IllegalArgumentException.class, () -> alter(original, target));
		Db2Federation.set(target, List.of(),
				List.of(new Server("S", "W", "ORACLE; DROP TABLE T", null, Map.of(), Map.of(), null, null)), List.of());
		assertThrows(IllegalArgumentException.class,
				() -> Db2FederationSql.create(target, DialectHolder.Db2_1215Dialect));
		Db2Federation.set(target, List.of(), List.of(),
				List.of(new Defaults("W", null, null, Map.of("CPU_RATIO", "2.0"))));
		assertThrows(IllegalArgumentException.class,
				() -> Db2FederationSql.create(target, DialectHolder.Db2_1215Dialect));
	}
}
