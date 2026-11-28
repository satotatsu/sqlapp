/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoleMemberReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.RoleMember;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

public class InformixRoleMemberReader extends RoleMemberReader {
	public InformixRoleMemberReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<RoleMember> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo version) {
		final List<RoleMember> result = new ArrayList<>();
		execute(connection, getSqlNodeCache().getString("roleMembers.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String grantable = getString(rs, "is_grantable");
				if (!"Y".equals(grantable) && !"N".equals(grantable))
					throw new IllegalStateException("Unknown Informix role grant option: " + grantable);
				final var member = new RoleMember();
				member.setMemberRoleName(InformixSecurityMetadata.required(rs, ROLE_NAME));
				member.setGranteeName(InformixSecurityMetadata.required(rs, GRANTEE));
				member.setAdmin("Y".equals(grantable));
				member.setDefault(member.getMemberRoleName().equals(getString(rs, "default_role")));
				result.add(member);
			}
		});
		return result;
	}
}
