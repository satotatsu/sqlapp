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
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads Vertica text indices. */
public class VirticaIndexReader extends IndexReader {

	protected VirticaIndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("indexes.sql");
		List<Index> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.add(createIndex(rs));
			}
		});
		return result;
	}

	protected Index createIndex(ExResultSet rs) throws SQLException {
		Index index = new Index(getString(rs, INDEX_NAME));
		index.setId(getString(rs, "INDEX_ID"));
		index.setSchemaName(getString(rs, "SOURCE_TABLE_SCHEMA_NAME"));
		index.setTableName(getString(rs, "SOURCE_TABLE_NAME"));
		index.setIndexType(IndexType.FullText);
		index.getColumns().add(new Column(getString(rs, "TEXT_COL")));
		setSpecifics(rs, "INDEX_SCHEMA_NAME", index);
		setSpecifics(rs, "TOKENIZER_NAME", index);
		setSpecifics(rs, "TOKENIZER_SCHEMA_NAME", index);
		setSpecifics(rs, "STEMMER_NAME", index);
		setSpecifics(rs, "STEMMER_SCHEMA_NAME", index);
		return index;
	}
}
