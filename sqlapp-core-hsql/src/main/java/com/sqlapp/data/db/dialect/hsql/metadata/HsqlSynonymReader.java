/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SynonymReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Optional SYSTEM_SYNONYMS catalog, probed once for the whole requested set.
 */
public class HsqlSynonymReader extends SynonymReader {
	protected HsqlSynonymReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Synonym> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		var result = new ArrayList<Synonym>();
		try (var tables = connection.getMetaData().getTables(null, "INFORMATION_SCHEMA", "SYSTEM_SYNONYMS", null)) {
			if (!tables.next())
				return result;
		} catch (SQLException e) {
			throw new IllegalStateException("Cannot inspect HSQLDB synonym catalog capability", e);
		}
		execute(connection, getSqlNodeCache().getString("synonyms.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				var synonym = new Synonym(getString(rs, "SYNONYM_NAME"));
				synonym.setCatalogName(getString(rs, "SYNONYM_CATALOG"));
				synonym.setSchemaName(getString(rs, "SYNONYM_SCHEMA"));
				synonym.setObjectSchemaName(getString(rs, "OBJECT_SCHEMA"));
				synonym.setObjectName(getString(rs, "OBJECT_NAME"));
				setSpecifics(rs, "OBJECT_TYPE", synonym);
				result.add(synonym);
			}
		});
		return result;
	}
}
