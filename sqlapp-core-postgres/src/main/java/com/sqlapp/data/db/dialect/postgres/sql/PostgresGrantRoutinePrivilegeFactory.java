package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.schemas.RoutinePrivilege;

/** EXECUTE grants from the existing routine privilege model. */
public class PostgresGrantRoutinePrivilegeFactory extends SimpleSqlFactory<RoutinePrivilege, PostgresSqlBuilder> {
	protected boolean isGrant() { return true; }

	@Override
	public List<SqlOperation> createSql(RoutinePrivilege privilege) {
		if (privilege.getPrivilege() != null && !"EXECUTE".equalsIgnoreCase(privilege.getPrivilege()))
			throw new IllegalArgumentException("PostgreSQL routine privilege must be EXECUTE");
		if (privilege.getState() != null && privilege.getState() != (isGrant() ? PrivilegeState.Grant : PrivilegeState.Revoke))
			throw new IllegalArgumentException("Routine privilege state conflicts with requested GRANT/REVOKE");
		String name = privilege.getObjectName();
		String signature = privilege.getSpecificName();
		if (name == null || name.isBlank() || signature == null || !signature.startsWith(name + "(") || !signature.endsWith(")"))
			throw new IllegalArgumentException("Routine privilege requires specificName with its full input signature, for example f(integer) or f()");
		String grantee = privilege.getGranteeName();
		if (grantee == null || grantee.isBlank()) throw new IllegalArgumentException("Routine privilege requires a grantee");
		if (isGrant() && "PUBLIC".equals(grantee) && privilege.isGrantable())
			throw new IllegalArgumentException("PUBLIC cannot receive WITH GRANT OPTION");
		String kind = privilege.getSpecifics().get("ROUTINE_KIND");
		if (kind != null && !"FUNCTION".equals(kind) && !"PROCEDURE".equals(kind))
			throw new IllegalArgumentException("Unsupported PostgreSQL routine kind: " + kind);
		if ("PROCEDURE".equals(kind) && !(getDialect() instanceof com.sqlapp.data.db.dialect.postgres.Postgres110))
			throw new IllegalArgumentException("PostgreSQL procedures require version 11 or later");
		PostgresSqlBuilder builder = createSqlBuilder();
		builder._add(isGrant() ? "GRANT EXECUTE ON " : "REVOKE EXECUTE ON ");
		builder._add("PROCEDURE".equals(kind) ? "PROCEDURE " : "FUNCTION ");
		if (getOptions().isDecorateSchemaName() && privilege.getSchemaName() != null)
			builder._add(quoteIdentifier(privilege.getSchemaName()))._add(".");
		builder._add(quoteIdentifier(name))._add(signature.substring(name.length()));
		builder._add(isGrant() ? " TO " : " FROM ");
		builder._add("PUBLIC".equals(grantee) ? "PUBLIC" : quoteIdentifier(grantee));
		if (isGrant() && privilege.isGrantable()) builder._add(" WITH GRANT OPTION");
		List<SqlOperation> operations = new ArrayList<>();
		addSql(operations, builder, isGrant() ? SqlType.GRANT : SqlType.REVOKE, privilege);
		return operations;
	}
	private String quoteIdentifier(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

}
