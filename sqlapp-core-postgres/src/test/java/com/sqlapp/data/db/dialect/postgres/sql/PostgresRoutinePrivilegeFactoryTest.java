package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.RoutinePrivilege;
import com.sqlapp.data.schemas.PrivilegeState;

class PostgresRoutinePrivilegeFactoryTest {
	private RoutinePrivilege privilege() {
		return new RoutinePrivilege().setSchemaName("Other Schema").setObjectName("f.name")
				.setSpecificName("f.name(\"Type Schema\".\"State.Type\"[])")
				.setPrivilege("EXECUTE").setGranteeName("Role.name").setGrantable(true);
	}
	@Test void generatesQuotedGrantRevokeAndPublicProcedureOperations() {
		var registry = DialectResolver.getInstance().getDialect("postgres", 15, 0, null).createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var p = privilege().setPrivilege(null);
		String sql = registry.createSql(p, SqlType.GRANT).get(0).getSqlText();
		assertEquals("GRANT EXECUTE ON FUNCTION \"Other Schema\".\"f.name\"(\"Type Schema\".\"State.Type\"[]) TO \"Role.name\" WITH GRANT OPTION", sql);
		assertFalse(registry.createSql(p, SqlType.REVOKE).get(0).getSqlText().contains("GRANT OPTION"));
		p.setObjectName("p").setSpecificName("p()").setGranteeName("PUBLIC").setGrantable(false);
		p.getSpecifics().put("ROUTINE_KIND", "PROCEDURE");
		assertTrue(registry.createSql(p, SqlType.GRANT).get(0).getSqlText().endsWith(".\"p\"() TO PUBLIC"));
	}
	@Test void escapesEmbeddedIdentifierQuotes() {
		var registry = DialectResolver.getInstance().getDialect("postgres", 15, 0, null).createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(false);
		var p = privilege().setObjectName("f\"name").setSpecificName("f\"name()").setGranteeName("role\"name");
		String sql = registry.createSql(p, SqlType.GRANT).get(0).getSqlText();
		assertTrue(sql.contains("FUNCTION \"f\"\"name\"()"), sql);
		assertTrue(sql.contains("TO \"role\"\"name\""), sql);
	}

	@Test void rejectsMissingSignaturesUnsupportedPrivilegesAndConflictingStates() {
		var registry = DialectResolver.getInstance().getDialect("postgres", 15, 0, null).createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(privilege().setSpecificName(null), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(privilege().setPrivilege("DELETE"), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(privilege().setGranteeName(null), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(privilege().setGranteeName("PUBLIC"), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(privilege().setState(PrivilegeState.Deny), SqlType.GRANT));
		var legacy = DialectResolver.getInstance().getDialect("postgres", 10, 0, null).createSqlFactoryRegistry();
		var procedure = privilege(); procedure.getSpecifics().put("ROUTINE_KIND", "PROCEDURE");
		assertThrows(IllegalArgumentException.class, () -> legacy.createSql(procedure, SqlType.GRANT));
	}
}
