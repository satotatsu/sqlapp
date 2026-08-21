/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoleMemberReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.RoleMember;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads explicit Vertica role grants. */
public class VirticaRoleMemberReader extends RoleMemberReader {
	protected VirticaRoleMemberReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<RoleMember> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("roleMembers.sql");
		List<RoleMember> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				RoleMember member = new RoleMember();
				member.setGranteeName(getString(rs, GRANTEE));
				member.setMemberRoleName(getString(rs, ROLE_NAME));
				setSpecifics(rs, "GRANTOR", member);
				result.add(member);
			}
		});
		return result;
	}
}
