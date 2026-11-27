/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.schemas.RoutinePrivilege;

class SapHanaRoutinePrivilegeSqlTest {
	@Test
	void grantsAndRevokesQuotedRoutineNames() {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.cloudDialect)) {
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			var privilege = new RoutinePrivilege().setSchemaName("Odd Schema").setObjectName("My\"Routine")
					.setGranteeName("Some Role").setPrivilege("EXECUTE").setGrantable(true);
			assertEquals("GRANT EXECUTE ON \"Odd Schema\".\"My\"\"Routine\" TO \"Some Role\" WITH GRANT OPTION",
					registry.createSql(privilege, SqlType.GRANT).getFirst().getSqlText());
			assertEquals("REVOKE EXECUTE ON \"Odd Schema\".\"My\"\"Routine\" FROM \"Some Role\"",
					registry.createSql(privilege, SqlType.REVOKE).getFirst().getSqlText());
			privilege.setPrivilege("SELECT");
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(privilege, SqlType.GRANT));
			privilege.setPrivilege("EXECUTE").setState(PrivilegeState.Revoke);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(privilege, SqlType.GRANT));
			privilege.setState((PrivilegeState) null).setGranteeName(null);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(privilege, SqlType.GRANT));
		}
	}
}
