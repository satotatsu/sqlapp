/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.metadata;

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

/** H2 2.x synonyms, fetched as one requested object set. */
public class H2SynonymReader extends SynonymReader {
	protected H2SynonymReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Synonym> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		List<Synonym> result = new ArrayList<>();
		execute(connection, getSqlNodeCache().getString("synonyms_200.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rows) throws SQLException {
				var synonym = new Synonym(getString(rows, "SYNONYM_NAME"));
				synonym.setCatalogName(getString(rows, "SYNONYM_CATALOG"));
				synonym.setSchemaName(getString(rows, "SYNONYM_SCHEMA"));
				synonym.setObjectSchemaName(getString(rows, "SYNONYM_FOR_SCHEMA"));
				synonym.setObjectName(getString(rows, "SYNONYM_FOR"));
				synonym.setRemarks(getString(rows, REMARKS));
				result.add(synonym);
			}
		});
		return result;
	}
}
