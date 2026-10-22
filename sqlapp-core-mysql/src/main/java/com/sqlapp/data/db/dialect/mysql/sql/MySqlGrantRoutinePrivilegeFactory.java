/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.sqlapp.data.db.dialect.mysql.util.MySqlAccount;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.schemas.RoutinePrivilege;

/** Native routine GRANT with an explicit object kind and complete account identity. */
public class MySqlGrantRoutinePrivilegeFactory extends SimpleSqlFactory<RoutinePrivilege, MySqlSqlBuilder> {
	protected boolean isGrant() { return true; }

	@Override
	public List<SqlOperation> createSql(RoutinePrivilege obj) {
		String kind = obj.getSpecifics().get("ROUTINE_KIND");
		String privilege = obj.getPrivilege();
		if (kind == null || !Set.of("FUNCTION", "PROCEDURE").contains(kind.toUpperCase(Locale.ROOT)))
			throw new IllegalArgumentException("MySQL routine privilege requires ROUTINE_KIND=FUNCTION or PROCEDURE");
		if (privilege == null || !Set.of("EXECUTE", "ALTER ROUTINE", "GRANT OPTION").contains(privilege.toUpperCase(Locale.ROOT)))
			throw new IllegalArgumentException("MySQL routine privilege requires EXECUTE, ALTER ROUTINE or GRANT OPTION");
		if (obj.getObjectName() == null || obj.getObjectName().isBlank() || obj.getSchemaName() == null || obj.getSchemaName().isBlank())
			throw new IllegalArgumentException("MySQL routine privilege requires its schema and routine name");
		if (obj.getState() != null && obj.getState() != (isGrant() ? PrivilegeState.Grant : PrivilegeState.Revoke))
			throw new IllegalArgumentException("Routine privilege state conflicts with requested GRANT/REVOKE");
		String account = MySqlAccount.sql(obj.getGranteeName());
		MySqlSqlBuilder builder = createSqlBuilder();
		builder._add(isGrant() ? "GRANT " : "REVOKE ")._add(privilege.toUpperCase(Locale.ROOT));
		builder._add(" ON ")._add(kind.toUpperCase(Locale.ROOT)).space();
		builder._add("`")._add(obj.getSchemaName().replace("`", "``"))._add("`.`")
				._add(obj.getObjectName().replace("`", "``"))._add("`");
		builder._add(isGrant() ? " TO " : " FROM ")._add(account);
		if (isGrant() && obj.isGrantable()) builder._add(" WITH GRANT OPTION");
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, isGrant() ? SqlType.GRANT : SqlType.REVOKE, obj);
		return result;
	}
}
