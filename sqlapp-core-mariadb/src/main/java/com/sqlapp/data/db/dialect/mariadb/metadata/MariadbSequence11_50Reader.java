/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.math.BigDecimal;
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
 * Reads MariaDB 11.5+ sequences from INFORMATION_SCHEMA.SEQUENCES.
 */
public class MariadbSequence11_50Reader extends SequenceReader {

	public MariadbSequence11_50Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Sequence> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("sequences.sql");
		List<Sequence> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Sequence sequence = new Sequence(getString(rs, "SEQUENCE_NAME"));
				sequence.setCatalogName(getString(rs, "SEQUENCE_CATALOG"));
				sequence.setSchemaName(getString(rs, "SEQUENCE_SCHEMA"));
				sequence.setRemarks(getString(rs, "TABLE_COMMENT"));
				String engine = getString(rs, "ENGINE");
				if (engine != null)
					sequence.getSpecifics().put("ENGINE", engine);
				final var type = new com.sqlapp.data.schemas.Column();
				getDialect().setDbType(getString(rs, "DATA_TYPE"), null, null, type);
				sequence.setDataType(type.getDataType());
				sequence.setDataTypeName(type.getDataTypeName());
				sequence.setPrecision(getInteger(rs, "NUMERIC_PRECISION"));
				sequence.setScale(getInteger(rs, "NUMERIC_SCALE"));
				sequence.setStartValue(toBigInteger(rs.getBigDecimal("START_VALUE")));
				sequence.setMinValue(toBigInteger(rs.getBigDecimal("MINIMUM_VALUE")));
				sequence.setMaxValue(toBigInteger(rs.getBigDecimal("MAXIMUM_VALUE")));
				sequence.setIncrementBy(toBigInteger(rs.getBigDecimal("INCREMENT")));
				sequence.setCycle(rs.getInt("CYCLE_OPTION") != 0);
				result.add(sequence);
			}
		});
		readCacheSizes(connection, result);
		return result;
	}

	private void readCacheSizes(Connection connection, List<Sequence> sequences) {
		int batchSize = MariadbSequence10_30Reader.BATCH_SIZE;
		for (int offset = 0; offset < sequences.size(); offset += batchSize) {
			List<Sequence> batch = sequences.subList(offset, Math.min(offset + batchSize, sequences.size()));
			boolean[] seen = new boolean[batch.size()];
			StringBuilder sql = new StringBuilder();
			for (int i = 0; i < batch.size(); i++) {
				if (i > 0)
					sql.append(" UNION ALL ");
				Sequence sequence = batch.get(i);
				sql.append("SELECT ").append(i).append(" AS sequence_id, cache_size FROM ")
						.append(MariadbSequence10_30Reader.quote(sequence.getSchemaName())).append('.')
						.append(MariadbSequence10_30Reader.quote(sequence.getName()));
			}
			SqlNode storageQuery = new SqlNode(sql.toString());
			storageQuery.setParameters(java.util.Collections.emptySet());
			execute(connection, storageQuery, new ParametersContext(), new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(ExResultSet rs) throws SQLException {
					int id = rs.getInt("sequence_id");
					if (id < 0 || id >= batch.size() || seen[id]) {
						throw new IllegalStateException("Invalid or duplicate MariaDB cache batch row id: " + id);
					}
					seen[id] = true;
					BigDecimal value = rs.getBigDecimal("cache_size");
					if (value == null || value.signum() < 0) {
						throw new IllegalStateException(
								"Missing or negative MariaDB sequence cache size: " + batch.get(id).getName());
					}
					int size = value.intValueExact();
					batch.get(id).setCacheSize(size).setCache(size > 0);
				}
			});
			for (int i = 0; i < seen.length; i++) {
				if (!seen[i])
					throw new IllegalStateException("Missing MariaDB cache storage row: " + batch.get(i).getName());
			}
		}
	}

	private static java.math.BigInteger toBigInteger(BigDecimal value) {
		return value == null ? null : value.toBigIntegerExact();
	}
}
