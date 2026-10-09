package com.sqlapp.data.db.dialect.postgres.sql;

/** Revokes EXECUTE; dependent grants retain PostgreSQL's default RESTRICT safety. */
public class PostgresRevokeRoutinePrivilegeFactory extends PostgresGrantRoutinePrivilegeFactory {
	@Override
	protected boolean isGrant() { return false; }
}
