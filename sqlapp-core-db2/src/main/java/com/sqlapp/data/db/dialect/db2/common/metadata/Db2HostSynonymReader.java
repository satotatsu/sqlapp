/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SynonymReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * One catalog query retains direct alias targets without resolving alias
 * chains.
 */
final class Db2HostSynonymReader extends SynonymReader {
	private final Class<?> resources;

	Db2HostSynonymReader(Dialect dialect, Class<?> resources) {
		super(dialect);
		this.resources = resources;
	}

	@Override
	protected List<Synonym> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		List<Synonym> result = new ArrayList<>();
		Set<List<String>> names = new HashSet<>();
		execute(connection, getSqlNodeCache(resources).getString("synonyms.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				var synonym = new Synonym(required(rs, "SYNONYM_NAME"));
				synonym.setSchemaName(required(rs, "SCHEMA_NAME"));
				if (!names.add(List.of(synonym.getSchemaName(), synonym.getName())))
					throw new SQLException(
							"Shared Synonym model cannot represent multiple alias kinds with the same schema/name: "
									+ synonym.getSchemaName() + "." + synonym.getName());
				synonym.setObjectSchemaName(required(rs, "TARGET_SCHEMA"));
				synonym.setObjectName(required(rs, "TARGET_NAME"));
				synonym.setDbLinkName(identifier(rs, "TARGET_LOCATION"));
				synonym.getSpecifics().put("ALIAS_TYPE", required(rs, "ALIAS_TYPE"));
				String kind = required(rs, "NAME_KIND");
				synonym.getSpecifics().put("DB2_HOST_NAME_KIND", kind);
				if ("SYNONYM".equals(kind))
					synonym.getSpecifics().put("DB2_HOST_REQUIRES_DEFINITION",
							"Legacy z/OS SYNONYM is inventory only; conversion to ALIAS changes semantics");
				for (String field : List.of("MEMBER_NAME", "BASE_CATALOG")) {
					String value = identifier(rs, field);
					if (value != null)
						synonym.getSpecifics().put("DB2_HOST_" + field, value);
				}
				synonym.setCreatedAt(rs.getTimestamp("CREATED_AT"));
				synonym.setLastAlteredAt(rs.getTimestamp("ALTERED_AT"));
				synonym.setRemarks(rs.getString("REMARKS"));
				result.add(synonym);
			}
		});
		return result;
	}

	private static String required(ExResultSet rs, String name) throws SQLException {
		String value = identifier(rs, name);
		if (value == null)
			throw new SQLException("Missing host alias catalog field: " + name);
		return value;
	}

	private static String identifier(ExResultSet rs, String name) throws SQLException {
		String value = rs.getString(name);
		return value == null || value.isBlank() ? null : value.stripTrailing();
	}
}
