/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.db.dialect.db2.Db2UserMappings.Mapping;
import com.sqlapp.data.db.dialect.db2.Db2UserMappings.Principal;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class Db2UserMappingSqlTest {
	private Mapping captured() {
		return new Mapping(Principal.USER, "Local.User", "Remote.Server", Map.of("REMOTE_AUTHID", "remote's user"),
				List.of("REMOTE_PASSWORD"), null);
	}

	private Catalog catalog(Mapping mapping) {
		Catalog result = new Catalog();
		Db2UserMappings.set(result, List.of(mapping));
		return result;
	}

	@Test
	void unreadableCredentialsRequireExplicitValuesAndNativeSqlQuotesLiterals() {
		Catalog captured = catalog(captured());
		assertThrows(IllegalArgumentException.class,
				() -> Db2UserMappingSql.create(captured, DialectHolder.Db2_1110Dialect));
		Catalog resolved = catalog(captured().withOption("REMOTE_PASSWORD", "example's password"));
		assertEquals(List.of(
				"CREATE USER MAPPING FOR \"Local.User\" SERVER \"Remote.Server\" OPTIONS (REMOTE_AUTHID 'remote''s user', REMOTE_PASSWORD 'example''s password')"),
				Db2UserMappingSql.create(resolved, DialectHolder.Db2_1110Dialect));
		assertThrows(IllegalArgumentException.class,
				() -> Db2UserMappingSql.create(resolved, DialectHolder.defaultDialect));
		var definition = new Mapping(Principal.USER, "Local.User", "Remote.Server", Map.of(),
				List.of("REMOTE_PASSWORD"),
				"CREATE USER MAPPING FOR USER SERVER S OPTIONS (REMOTE_PASSWORD 'example')");
		assertEquals(List.of(definition.definition()),
				Db2UserMappingSql.create(catalog(definition), DialectHolder.Db2_1110Dialect));
	}

	@Test
	void nativeAlterPreservesUnchangedUnavailablePasswordsAndUsesSetForResolvedValues() {
		var original = captured();
		var changed = original.withOption("REMOTE_AUTHID", "new id").withOption("CUSTOM", "x");
		var plan = Db2UserMappingSql.diff(catalog(original), catalog(changed), DialectHolder.Db2_1110Dialect);
		assertTrue(plan.before().isEmpty());
		assertEquals(List.of(
				"ALTER USER MAPPING FOR \"Local.User\" SERVER \"Remote.Server\" OPTIONS (ADD CUSTOM 'x', SET REMOTE_AUTHID 'new id')"),
				plan.after());
		assertTrue(Db2UserMappingSql.diff(catalog(changed), catalog(original), DialectHolder.Db2_1110Dialect).after()
				.getFirst().contains("DROP CUSTOM, SET REMOTE_AUTHID"));
		var resolved = original.withOption("REMOTE_PASSWORD", "example");
		assertTrue(Db2UserMappingSql.diff(catalog(original), catalog(resolved), DialectHolder.Db2_1110Dialect).after()
				.getFirst().contains("SET REMOTE_PASSWORD 'example'"));
		assertTrue(Db2UserMappingSql.diff(catalog(original), catalog(original), DialectHolder.Db2_1110Dialect).after()
				.isEmpty());
		assertThrows(IllegalArgumentException.class,
				() -> Db2UserMappingSql.diff(catalog(resolved), catalog(original), DialectHolder.Db2_1110Dialect));
		var missingPassword = new Mapping(Principal.USER, "Local.User", "Remote.Server",
				Map.of("REMOTE_AUTHID", "new id"), List.of(), null);
		assertThrows(IllegalArgumentException.class, () -> Db2UserMappingSql.diff(catalog(original),
				catalog(missingPassword), DialectHolder.Db2_1110Dialect));
		assertEquals("remote's user",
				Db2UserMappings.get(catalog(original)).mappings().getFirst().options().get("REMOTE_AUTHID"));
	}

	@Test
	void completeDefinitionCannotBeChangedThroughSupplementalOptions() {
		var original = new Mapping(Principal.USER, "U", "S", Map.of("REMOTE_AUTHID", "old"), List.of("REMOTE_PASSWORD"),
				"CREATE USER MAPPING FOR U SERVER S OPTIONS (REMOTE_PASSWORD 'example')");
		assertThrows(IllegalArgumentException.class, () -> Db2UserMappingSql.diff(catalog(original),
				catalog(original.withOption("REMOTE_AUTHID", "new")), DialectHolder.Db2_1110Dialect));
		var changed = new Mapping(Principal.USER, "U", "S", original.options(), original.unavailableOptions(),
				original.definition() + " ");
		assertThrows(IllegalArgumentException.class,
				() -> Db2UserMappingSql.diff(catalog(original), catalog(changed), DialectHolder.Db2_1110Dialect));
		assertTrue(Db2UserMappingSql.diff(catalog(original), catalog(original), DialectHolder.Db2_1110Dialect).after()
				.isEmpty());
	}

	@Test
	void publicTransitionDropsIndividualMappingsBeforeCreationAndChecksProxyConflicts() {
		var publicMapping = new Mapping(Principal.PUBLIC, "PUBLIC", "Remote.Server",
				Map.of("REMOTE_PASSWORD", "example"), List.of(), null);
		var plan = Db2UserMappingSql.diff(catalog(captured()), catalog(publicMapping), DialectHolder.Db2_1110Dialect);
		assertEquals(List.of("DROP USER MAPPING FOR \"Local.User\" SERVER \"Remote.Server\""), plan.before());
		assertTrue(plan.after().getFirst().startsWith("CREATE USER MAPPING FOR PUBLIC"));
		Catalog target = catalog(publicMapping);
		Db2Federation.set(target, List.of(), List.of(new Db2Federation.Server("Remote.Server", "W", "ORACLE", null,
				Map.of("FED_PROXY_USER", "Y"), Map.of(), null, null)), List.of());
		assertThrows(IllegalArgumentException.class,
				() -> Db2UserMappingSql.create(target, DialectHolder.Db2_1110Dialect));
		assertEquals(List.of("DROP USER MAPPING FOR \"Local.User\" SERVER \"Remote.Server\""),
				Db2UserMappingSql.diff(catalog(captured()), new Catalog(), DialectHolder.Db2_1110Dialect).before());
	}

	@Test
	void catalogCreateAndAlterPlaceMappingsAfterServerAndBeforeNickname() throws Exception {
		Catalog target = catalog(captured().withOption("REMOTE_PASSWORD", "example"));
		String server = "CREATE SERVER \"Remote.Server\" TYPE ORACLE VERSION '9i' WRAPPER NET8";
		Db2Federation.set(target, List.of(),
				List.of(new Db2Federation.Server("Remote.Server", null, null, null, Map.of(), Map.of(), null, server)),
				List.of());
		Schema schema = new Schema("APP");
		target.getSchemas().add(schema);
		Table nickname = new Table("N");
		schema.getTables().add(nickname);
		Db2Nickname.set(nickname,
				new Db2Nickname.Snapshot(1, "Remote.Server", "APP", "T", "R", "N", Map.of(), Map.of()));
		nickname.getDefinition().add("CREATE NICKNAME APP.N FOR \"Remote.Server\".APP.T");
		Catalog restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		var registry = DialectHolder.Db2_1215Dialect.createSqlFactoryRegistry();
		SqlFactory<Catalog> create = registry.getSqlFactory(restored, SqlType.CREATE);
		var sql = create.createSql(restored).stream().map(o -> o.getSqlText()).toList();
		assertEquals(server, sql.getFirst());
		assertTrue(sql.get(1).startsWith("CREATE USER MAPPING"));
		assertTrue(sql.indexOf(nickname.getDefinition().getFirst()) > 1, sql.toString());
		SqlFactory<Catalog> alter = registry.getSqlFactory(restored, SqlType.ALTER);
		var added = alter.createDiffSql(new Catalog().diff(restored)).stream().map(o -> o.getSqlText()).toList();
		assertEquals(server, added.getFirst());
		assertTrue(added.get(1).startsWith("CREATE USER MAPPING"));
		assertTrue(added.indexOf(nickname.getDefinition().getFirst()) > 1);
		Catalog removed = restored.clone();
		Db2UserMappings.set(removed, List.of());
		assertTrue(alter.createDiffSql(restored.diff(removed)).getFirst().getSqlText().startsWith("DROP USER MAPPING"));
		assertEquals(1, Db2UserMappings.get(restored).mappings().size());
	}
}
