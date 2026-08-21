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
import com.sqlapp.data.db.metadata.SequenceReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads Informix sequence definitions from {@code syssequences}. */
public class InformixSequenceReader extends SequenceReader {
	public InformixSequenceReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Sequence> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("sequences.sql");
		List<Sequence> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				Sequence sequence = new Sequence(getString(rs, SEQUENCE_NAME));
				sequence.setCatalogName(getString(rs, CATALOG_NAME));
				sequence.setSchemaName(getString(rs, SCHEMA_NAME));
				sequence.setStartValue(rs.getLong("start_value"));
				sequence.setIncrementBy(rs.getLong("increment_value"));
				sequence.setMinValue(rs.getLong("minimum_value"));
				sequence.setMaxValue(rs.getLong("maximum_value"));
				sequence.setCycle(rs.getInt("cycle_option") != 0);
				sequence.setCacheSize(rs.getLong("cache_size"));
				result.add(sequence);
			}
		});
		return result;
	}
}
