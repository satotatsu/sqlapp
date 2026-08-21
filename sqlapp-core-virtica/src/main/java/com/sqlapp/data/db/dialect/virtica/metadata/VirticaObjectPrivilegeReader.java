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
import com.sqlapp.data.db.metadata.ObjectPrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads explicitly granted Vertica object privileges. */
public class VirticaObjectPrivilegeReader extends ObjectPrivilegeReader {
	protected VirticaObjectPrivilegeReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ObjectPrivilege> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("objectPrivileges.sql");
		List<ObjectPrivilege> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				String descriptions = getString(rs, "privileges_description");
				if (descriptions == null) {
					return;
				}
				for (String description : descriptions.split(",")) {
					String privilege = description.trim();
					if (privilege.isEmpty()) {
						continue;
					}
					boolean grantable = privilege.endsWith("*");
					if (grantable) {
						privilege = privilege.substring(0, privilege.length() - 1).trim();
					}
					ObjectPrivilege objectPrivilege = new ObjectPrivilege();
					objectPrivilege.setCatalogName(getString(rs, CATALOG_NAME));
					objectPrivilege.setSchemaName(getString(rs, SCHEMA_NAME));
					objectPrivilege.setObjectName(getString(rs, OBJECT_NAME));
					objectPrivilege.setPrivilege(privilege);
					objectPrivilege.setGrantorName(getString(rs, GRANTOR));
					objectPrivilege.setGranteeName(getString(rs, GRANTEE));
					objectPrivilege.setGrantable(grantable);
					setSpecifics(rs, "object_type", objectPrivilege);
					result.add(objectPrivilege);
				}
			}
		});
		return result;
	}
}
