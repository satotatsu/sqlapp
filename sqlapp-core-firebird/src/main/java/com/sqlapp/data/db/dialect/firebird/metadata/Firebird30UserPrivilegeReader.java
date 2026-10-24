/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdDdlPrivileges;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.metadata.UserPrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UserPrivilege;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Database-local DDL grants, with optional server-security CREATE DATABASE
 * grants.
 */
public class Firebird30UserPrivilegeReader extends UserPrivilegeReader {
	private boolean readDatabaseCreators;

	protected Firebird30UserPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	/**
	 * Whether to include grants visible in the connection's security database.
	 * Defaults to false.
	 */
	public boolean isReadDatabaseCreators() {
		return readDatabaseCreators;
	}

	/** Opt in to reading SEC$DB_CREATORS; access failures are propagated. */
	public void setReadDatabaseCreators(boolean readDatabaseCreators) {
		this.readDatabaseCreators = readDatabaseCreators;
	}

	@Override
	protected List<UserPrivilege> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		List<UserPrivilege> result = new ArrayList<>();
		execute(connection, getSqlSqlNode(), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.add(createPrivilege(rs));
			}
		});
		if (readDatabaseCreators) {
			execute(connection, getSqlNodeCache().getString("databaseCreators.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							UserPrivilege privilege = new UserPrivilege().setPrivilege("CREATE DATABASE");
							privilege.setGranteeName(getString(rs, "grantee"));
							privilege.getSpecifics().put(FirebirdPrivilege.GRANTEE_TYPE,
									FirebirdPrivilege.granteeType(rs.getInt("grantee_type")));
							result.add(privilege);
						}
					});
		}
		return result;
	}

	protected com.sqlapp.jdbc.sql.node.SqlNode getSqlSqlNode() {
		return getSqlNodeCache().getString("userPrivileges30.sql");
	}

	protected UserPrivilege createPrivilege(ExResultSet rs) throws SQLException {
		UserPrivilege privilege = new UserPrivilege();
		privilege.setPrivilege(FirebirdDdlPrivileges.fromCatalog(getString(rs, "privilege_type"),
				rs.getInt("object_type"), getDialect()));
		privilege.setGranteeName(getString(rs, "grantee"));
		privilege.setGrantorName(getString(rs, "grantor"));
		privilege.setGrantable(rs.getInt("grant_option") == 1);
		privilege.getSpecifics().put(FirebirdPrivilege.GRANTEE_TYPE,
				FirebirdPrivilege.granteeType(rs.getInt("grantee_type")));
		return privilege;
	}
}
