/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.Db2ObjectPrivileges.*;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.SchemaUtils;

class Db2ObjectPrivilegesTest {
	private Grant grant(Kind kind, String action, String grantor, boolean grantable) {
		return new Grant(kind, "App.Schema", "Same.Name", action, grantor, GranteeType.ROLE, "R\"ole", grantable);
	}

	@Test
	void xmlCloneAndDiffRetainKindsGrantorsAndGrantability() throws Exception {
		Catalog catalog = new Catalog();
		var grants = List.of(grant(Kind.SEQUENCE, "ALTER", "A", true), grant(Kind.INDEX, "CONTROL", "A", false));
		Db2ObjectPrivileges.set(catalog, grants);
		Catalog restored = SchemaUtils.readXml(new StringReader(catalog.asXml()));
		assertEquals(grants, Db2ObjectPrivileges.get(restored).grants());
		assertEquals(grants, Db2ObjectPrivileges.get(catalog.clone()).grants());
		assertTrue(Db2ObjectPrivileges.createSql(catalog).contains(
				"GRANT ALTER ON SEQUENCE \"App.Schema\".\"Same.Name\" TO ROLE \"R\"\"ole\" WITH GRANT OPTION"));
		assertTrue(Db2ObjectPrivileges.createSql(catalog)
				.contains("GRANT CONTROL ON INDEX \"App.Schema\".\"Same.Name\" TO ROLE \"R\"\"ole\""));
		Catalog empty = catalog.clone();
		empty.getSpecifics().put("OTHER", "preserved");
		Db2ObjectPrivileges.set(empty, List.of());
		assertFalse(empty.getSpecifics().containsKey(Db2ObjectPrivileges.SPECIFICS_KEY));
		assertEquals("preserved", empty.getSpecifics().get("OTHER"));
		assertEquals(2, Db2ObjectPrivileges.diff(empty, catalog).afterSql().size());
		assertEquals(2, Db2ObjectPrivileges.diff(catalog, empty).beforeSql().size());
		assertNotEquals(catalog, empty);
	}

	@Test
	void multipleGrantorsAreRetainedButNativeReplayAndDiffUseEffectiveScope() {
		Catalog original = new Catalog(), target = new Catalog();
		Db2ObjectPrivileges.set(original,
				List.of(grant(Kind.SEQUENCE, "USAGE", "A", true), grant(Kind.SEQUENCE, "USAGE", "B", false)));
		assertEquals(2, Db2ObjectPrivileges.get(original).grants().size());
		assertEquals(1, Db2ObjectPrivileges.createSql(original).size());
		Db2ObjectPrivileges.set(target, List.of(grant(Kind.SEQUENCE, "USAGE", "C", true)));
		assertTrue(Db2ObjectPrivileges.diff(original, target).beforeSql().isEmpty());
		assertTrue(Db2ObjectPrivileges.diff(original, target).afterSql().isEmpty());
		Db2ObjectPrivileges.set(target, List.of(grant(Kind.SEQUENCE, "USAGE", "B", false)));
		assertThrows(IllegalArgumentException.class, () -> Db2ObjectPrivileges.diff(original, target));
		assertEquals(1, Db2ObjectPrivileges.diff(target, original).afterSql().size());
	}

	@Test
	void serverPrivilegesRetainGlobalIdentityAndRejectGrantOption() throws Exception {
		Catalog catalog = new Catalog();
		var server = new Grant(Kind.SERVER, null, "Same.Name", "PASSTHRU", "A", GranteeType.ROLE, "R\"ole", false);
		var otherGrantor = new Grant(Kind.SERVER, null, "Same.Name", "PASSTHRU", "B", GranteeType.ROLE, "R\"ole",
				false);
		Db2ObjectPrivileges.set(catalog, List.of(server, otherGrantor, grant(Kind.SEQUENCE, "USAGE", "A", false)));
		Catalog restored = SchemaUtils.readXml(new StringReader(catalog.asXml()));
		assertEquals(Db2ObjectPrivileges.get(catalog), Db2ObjectPrivileges.get(restored));
		assertEquals(Db2ObjectPrivileges.get(catalog), Db2ObjectPrivileges.get(catalog.clone()));
		assertEquals(2, Db2ObjectPrivileges.createSql(restored).size());
		assertEquals("GRANT PASSTHRU ON SERVER \"Same.Name\" TO ROLE \"R\"\"ole\"",
				Db2ObjectPrivileges.grantSql(server));
		assertEquals("REVOKE PASSTHRU ON SERVER \"Same.Name\" FROM ROLE \"R\"\"ole\"",
				Db2ObjectPrivileges.revokeSql(server));
		Db2ObjectPrivileges.set(restored, List.of(otherGrantor, grant(Kind.SEQUENCE, "USAGE", "A", false)));
		assertTrue(Db2ObjectPrivileges.diff(catalog, restored).beforeSql().isEmpty());
		assertThrows(IllegalArgumentException.class,
				() -> new Grant(Kind.SERVER, "APP", "S", "PASSTHRU", null, GranteeType.USER, "U", false));
		assertThrows(IllegalArgumentException.class,
				() -> new Grant(Kind.SERVER, null, "S", "USAGE", null, GranteeType.USER, "U", false));
		assertThrows(IllegalArgumentException.class,
				() -> new Grant(Kind.SERVER, null, "S", "PASSTHRU", null, GranteeType.USER, "U", true));
	}

	@Test
	void nativeRevokesRetainScopeAndSequenceRestrict() {
		assertEquals("REVOKE USAGE ON SEQUENCE \"App.Schema\".\"Same.Name\" FROM ROLE \"R\"\"ole\" RESTRICT",
				Db2ObjectPrivileges.revokeSql(grant(Kind.SEQUENCE, "USAGE", null, true)));
		assertFalse(Db2ObjectPrivileges.revokeSql(grant(Kind.INDEX, "CONTROL", null, false)).endsWith("RESTRICT"));
		var publicGrant = new Grant(Kind.SEQUENCE, "APP", "S", "USAGE", null, GranteeType.PUBLIC, "PUBLIC", false);
		assertTrue(Db2ObjectPrivileges.grantSql(publicGrant).endsWith("TO PUBLIC"));
		assertThrows(IllegalArgumentException.class,
				() -> new Grant(Kind.SEQUENCE, "APP", "S", "USAGE", null, GranteeType.PUBLIC, "X", false));
	}

	@Test
	void invalidAndDuplicatePayloadsCannotSilentlyBecomeTableGrants() {
		assertThrows(IllegalArgumentException.class, () -> grant(Kind.SEQUENCE, "CONTROL", null, false));
		assertThrows(IllegalArgumentException.class, () -> grant(Kind.INDEX, "SELECT", null, false));
		assertThrows(IllegalArgumentException.class, () -> grant(Kind.INDEX, "CONTROL", null, true));
		var grant = grant(Kind.SEQUENCE, "USAGE", null, false);
		assertThrows(IllegalArgumentException.class, () -> new Snapshot(1, List.of(grant, grant)));
		assertThrows(IllegalArgumentException.class,
				() -> new Grant(Kind.SEQUENCE, "APP", "S", "USAGE", null, GranteeType.USER, "U", null));
		Catalog catalog = new Catalog();
		for (String payload : List.of("null", "{}", "{\"format\":2,\"grants\":[]}",
				"{\"format\":1,\"grants\":[],\"unknown\":true}")) {
			catalog.getSpecifics().put(Db2ObjectPrivileges.SPECIFICS_KEY, payload);
			assertThrows(RuntimeException.class, () -> Db2ObjectPrivileges.get(catalog));
		}
	}
}
