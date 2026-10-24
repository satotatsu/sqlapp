/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.schemas.RoutinePrivilege;

class MySqlRoutinePrivilegeSqlTest {
	@ParameterizedTest
	@CsvSource({ "5,5", "5,7", "8,0", "8,4", "9,0" })
	void registryGeneratesNativeRoutineGrantsAndRevokes(int major, int minor) {
		var dialect = DialectResolver.getInstance().getDialect("MySql", major, minor, 0);
		for (String kind : new String[] { "FUNCTION", "PROCEDURE" }) {
			for (String privilege : new String[] { "EXECUTE", "ALTER ROUTINE", "GRANT OPTION" }) {
				RoutinePrivilege obj = privilege(kind, privilege);
				SqlFactory<RoutinePrivilege> grant = dialect.createSqlFactoryRegistry().getSqlFactory(obj,
						SqlType.GRANT);
				SqlFactory<RoutinePrivilege> revoke = dialect.createSqlFactoryRegistry().getSqlFactory(obj,
						SqlType.REVOKE);
				assertEquals(
						"GRANT " + privilege + " ON " + kind + " `other app`.`select` TO `O'Reilly``reader`@`10.0.%`",
						grant.createSql(obj).get(0).getSqlText());
				assertEquals(
						"REVOKE " + privilege + " ON " + kind
								+ " `other app`.`select` FROM `O'Reilly``reader`@`10.0.%`",
						revoke.createSql(obj).get(0).getSqlText());
				obj.setGrantable(true);
				assertTrue(grant.createSql(obj).get(0).getSqlText().endsWith(" WITH GRANT OPTION"));
			}
		}
	}

	@Test
	void rejectsAmbiguousKindsAccountsAndUnsupportedOptions() {
		var dialect = DialectResolver.getInstance().getDialect("MySql", 8, 4, 0);
		RoutinePrivilege obj = privilege("FUNCTION", "EXECUTE");
		SqlFactory<RoutinePrivilege> factory = dialect.createSqlFactoryRegistry().getSqlFactory(obj, SqlType.GRANT);
		obj.getSpecifics().clear();
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(obj));
		obj.getSpecifics().put("ROUTINE_KIND", "FUNCTION");
		obj.setGranteeName("reader");
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(obj));
		obj.setGranteeName("'reader'@'%'");
		obj.setState(PrivilegeState.Revoke);
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(obj));
		obj.setState(PrivilegeState.Grant);
		obj.setPrivilege("CREATE ROUTINE");
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(obj));
		obj.setPrivilege("EXECUTE");
		obj.setSchemaName(null);
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(obj));
	}

	private RoutinePrivilege privilege(String kind, String privilege) {
		RoutinePrivilege obj = new RoutinePrivilege();
		obj.setSchemaName("other app");
		obj.setObjectName("select");
		obj.getSpecifics().put("ROUTINE_KIND", kind);
		obj.setPrivilege(privilege);
		obj.setGranteeName("'O''Reilly`reader'@'10.0.%'");
		return obj;
	}
}
