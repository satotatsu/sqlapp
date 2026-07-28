/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.jdbc.ExResultSet;

public class Oracle23aiIndexReader extends OracleIndexReader {

	public static final String ORGANIZATION = "ORGANIZATION";

	public Oracle23aiIndexReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void setIndexType(final ExResultSet rs, final Index index,
			final String productIndexType) throws SQLException {
		if (!"VECTOR".equalsIgnoreCase(productIndexType)) {
			return;
		}
		index.setIndexType(IndexType.Vector);
		final String subtype = getString(rs, "INDEX_SUBTYPE");
		if ("INMEMORY_NEIGHBOR_GRAPH_HNSW".equalsIgnoreCase(subtype)) {
			index.getSpecifics().put(ORGANIZATION, "HNSW");
		} else if ("NEIGHBOR_PARTITIONS_IVF".equalsIgnoreCase(subtype)) {
			index.getSpecifics().put(ORGANIZATION, "IVF");
		}
		if (subtype != null) {
			index.getSpecifics().put("INDEX_SUBTYPE", subtype);
		}
	}
}
