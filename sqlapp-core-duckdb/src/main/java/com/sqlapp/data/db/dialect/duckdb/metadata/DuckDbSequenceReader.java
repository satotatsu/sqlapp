/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.metadata;

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

/**
 * Reads DuckDB sequences from {@code duckdb_sequences()}. DuckDB sequences are
 * BIGINT based and have no cache setting.
 */
public class DuckDbSequenceReader extends SequenceReader {

	protected DuckDbSequenceReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Sequence> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlNodeCache().getString("sequences.sql");
		final List<Sequence> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Sequence sequence = new Sequence(getString(rs, SEQUENCE_NAME));
				sequence.setDialect(getDialect());
				sequence.setCatalogName(getString(rs, "sequence_catalog"));
				sequence.setSchemaName(getString(rs, "sequence_schema"));
				sequence.setStartValue(rs.getBigDecimal("start_value"));
				sequence.setMinValue(rs.getBigDecimal("min_value"));
				sequence.setMaxValue(rs.getBigDecimal("max_value"));
				sequence.setIncrementBy(rs.getBigDecimal("increment_by"));
				sequence.setCycle(rs.getBoolean("cycle"));
				sequence.setLastValue(rs.getBigDecimal("last_value"));
				sequence.setRemarks(getString(rs, REMARKS));
				result.add(sequence);
			}
		});
		return result;
	}
}
