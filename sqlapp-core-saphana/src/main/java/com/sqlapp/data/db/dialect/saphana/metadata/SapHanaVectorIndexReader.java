/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 */
package com.sqlapp.data.db.dialect.saphana.metadata;

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
import com.sqlapp.data.schemas.VectorDistanceType;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Reads SAP HANA Cloud vector indexes from {@code VECTOR_INDEXES}.
 */
public class SapHanaVectorIndexReader extends IndexReader {

	public SapHanaVectorIndexReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Index> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs)
					throws SQLException {
				result.add(createIndex(rs));
			}
		});
		return result;
	}

	protected Index createIndex(final ExResultSet rs) throws SQLException {
		final Index index = new Index(getString(rs, INDEX_NAME));
		index.setSchemaName(getString(rs, SCHEMA_NAME));
		index.setTableName(getString(rs, TABLE_NAME));
		index.setIndexType(IndexType.Vector);
		index.getColumns().add(new Column(getString(rs, COLUMN_NAME)));
		index.setVectorDistanceType(toVectorDistanceType(
				getString(rs, "SIMILARITY_FUNCTION")));
		setSpecifics(rs, "INDEX_TYPE", index);
		setSpecifics(rs, "BUILD_CONFIGURATION", index);
		setSpecifics(rs, "SEARCH_CONFIGURATION", index);
		return index;
	}

	static VectorDistanceType toVectorDistanceType(
			final String similarityFunction) {
		if ("COSINE_SIMILARITY".equalsIgnoreCase(similarityFunction)) {
			return VectorDistanceType.Cosine;
		}
		if ("L2DISTANCE".equalsIgnoreCase(similarityFunction)
				|| "L2_DISTANCE".equalsIgnoreCase(similarityFunction)) {
			return VectorDistanceType.Euclidean;
		}
		return null;
	}

	protected SqlNode getSqlSqlNode(
			final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("vectorIndexes.sql");
	}
}
