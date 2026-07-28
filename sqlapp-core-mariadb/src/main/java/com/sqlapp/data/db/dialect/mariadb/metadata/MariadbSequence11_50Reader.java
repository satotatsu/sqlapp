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
				sequence.setDataTypeName(getString(rs, "DATA_TYPE"));
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
		return result;
	}

	private static java.math.BigInteger toBigInteger(BigDecimal value) {
		return value == null ? null : value.toBigInteger();
	}
}
