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
	public static final String INFORMIX_ANSI_DATABASE = "INFORMIX_ANSI_DATABASE";

	public InformixSynonymReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Synonym> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
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
		final Synonym synonym = new Synonym(getString(rs, "object_name"));
		synonym.setCatalogName(getString(rs, CATALOG_NAME));
		synonym.setSchemaName(getString(rs, SCHEMA_NAME));
		synonym.setObjectSchemaName(getString(rs, "base_schema"));
		synonym.setObjectName(getString(rs, "base_object"));
		putSpecific(synonym, INFORMIX_DATABASE_NAME, getString(rs, "base_catalog"));
		putSpecific(synonym, INFORMIX_SYNONYM_TYPE, getString(rs, "synonym_type"));
		putSpecific(synonym, INFORMIX_SERVER_NAME, getString(rs, "server_name"));
		final String ansi = getString(rs, "ansi_database");
		if (!"0".equals(ansi) && !"1".equals(ansi))
			throw new IllegalStateException("Cannot resolve Informix database ANSI mode: " + synonym.getName());
		putSpecific(synonym, INFORMIX_ANSI_DATABASE, Boolean.toString("1".equals(ansi)));
		if (synonym.getObjectName() == null || synonym.getObjectSchemaName() == null)
			throw new IllegalStateException("Cannot resolve Informix synonym target: " + synonym.getName());
		return synonym;
	}

	private void putSpecific(final Synonym synonym, final String key, final String value) {
		if (value != null && !value.isBlank()) {
			synonym.getSpecifics().put(key, value);
		}
	}
}
