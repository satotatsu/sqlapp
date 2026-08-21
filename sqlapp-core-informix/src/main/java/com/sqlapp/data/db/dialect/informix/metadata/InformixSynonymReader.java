/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SynonymReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Informix synonym reader backed by {@code syssyntable}. */
public class InformixSynonymReader extends SynonymReader {
	public static final String INFORMIX_SYNONYM_TYPE = "INFORMIX_SYNONYM_TYPE";
	public static final String INFORMIX_DATABASE_NAME = "INFORMIX_DATABASE_NAME";
	public static final String INFORMIX_SERVER_NAME = "INFORMIX_SERVER_NAME";

	public InformixSynonymReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Synonym> doGetAll(final Connection connection,
			final ParametersContext context, final ProductVersionInfo productVersionInfo) {
		final List<Synonym> result = list();
		execute(connection, getSqlNode(productVersionInfo), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				result.add(createSynonym(rs));
			}
		});
		return result;
	}

	protected SqlNode getSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("synonyms.sql");
	}

	protected Synonym createSynonym(final ExResultSet rs) throws SQLException {
		final Synonym synonym = new Synonym(rs.getString(3));
		synonym.setSchemaName(rs.getString(2));
		synonym.setObjectSchemaName(rs.getString(5));
		synonym.setObjectName(rs.getString(6));
		putSpecific(synonym, INFORMIX_DATABASE_NAME, rs.getString(4));
		putSpecific(synonym, INFORMIX_SYNONYM_TYPE, rs.getString(7));
		putSpecific(synonym, INFORMIX_SERVER_NAME, rs.getString(8));
		return synonym;
	}

	private void putSpecific(final Synonym synonym, final String key, final String value) {
		if (value != null) {
			synonym.getSpecifics().put(key, value);
		}
	}
}
