/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.RoleMember;
import com.sqlapp.data.schemas.SchemaUtils;

class SpannerDatabaseAdministrationTest {
	@Test
	void createsDropsAndAssignsModeledDatabaseRoles() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var reader = new Role("Reader");
		final var app = new Role("App");
		assertEquals("CREATE ROLE `Reader`", registry.createSql(reader, SqlType.CREATE).getFirst().getSqlText());
		assertEquals("DROP ROLE `Reader`", registry.createSql(reader, SqlType.DROP).getFirst().getSqlText());
		final var member = new RoleMember(app, reader);
		assertEquals("GRANT ROLE `Reader` TO ROLE `App`",
				registry.createSql(member, SqlType.GRANT).getFirst().getSqlText());
		assertEquals("REVOKE ROLE `Reader` FROM ROLE `App`",
				registry.createSql(member, SqlType.REVOKE).getFirst().getSqlText());
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Role("public"), SqlType.DROP));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Role("spanner_reader"), SqlType.CREATE));
		member.setAdmin(true);
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(member, SqlType.GRANT));
	}

	@Test
	void ordersRoleMembershipAfterRoleCreationAndBeforeRemovalThroughCatalogXml() throws Exception {
		final var original = new Catalog("test-db").setDialect(DialectHolder.defaultDialect);
		final var target = original.clone();
		target.getRoles().add(new Role("App"));
		target.getRoles().add(new Role("Reader"));
		target.getRoleMembers().add(new RoleMember(target.getRoles().get("App"), target.getRoles().get("Reader")));
		final Catalog restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var added = registry.createSql(original.diff(restored));
		assertEquals(3, added.size());
		assertTrue(added.getFirst().getSqlText().startsWith("CREATE ROLE"));
		assertTrue(added.getLast().getSqlText().startsWith("GRANT ROLE"));
		restored.setDialect(DialectHolder.defaultDialect);
		final var removed = registry.createSql(restored.diff(original));
		assertEquals(3, removed.size());
		assertTrue(removed.getFirst().getSqlText().startsWith("REVOKE ROLE"));
		assertTrue(removed.getLast().getSqlText().startsWith("DROP ROLE"));
	}

	@Test
	void changesAndResetsDatabaseOptionsWithValidatedValues() {
		final var original = new Catalog("test-db").setDialect(DialectHolder.defaultDialect);
		final var target = original.clone();
		target.getSpecifics().put(SpannerAlterCatalogFactory.DEFAULT_SEQUENCE_KIND, "bit_reversed_positive");
		target.getSpecifics().put(SpannerAlterCatalogFactory.VERSION_RETENTION_PERIOD, "24h");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertEquals("ALTER DATABASE `test-db` SET OPTIONS (default_sequence_kind = 'bit_reversed_positive', "
				+ "version_retention_period = '24h')", registry.createSql(original.diff(target)).getFirst().getSqlText());
		target.setDialect(DialectHolder.defaultDialect);
		assertEquals("ALTER DATABASE `test-db` SET OPTIONS (default_sequence_kind = NULL, version_retention_period = NULL)",
				registry.createSql(target.diff(original)).getFirst().getSqlText());
		target.getSpecifics().put(SpannerAlterCatalogFactory.VERSION_RETENTION_PERIOD, "30d");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target)));
		target.getSpecifics().clear();
		target.getSpecifics().put("UNSUPPORTED_OPTION", "true");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(target)));
	}
}
