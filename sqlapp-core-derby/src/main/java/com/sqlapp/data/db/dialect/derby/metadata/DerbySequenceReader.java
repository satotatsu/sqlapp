/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-derby.
 */
package com.sqlapp.data.db.dialect.derby.metadata;

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

/** Reads Derby sequences from SYS.SYSSEQUENCES. */
public class DerbySequenceReader extends SequenceReader {

	protected DerbySequenceReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Sequence> doGetAll(Connection connection,
			ParametersContext context,
			ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("sequences.sql");
		List<Sequence> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Sequence sequence = new Sequence(getString(rs, SEQUENCE_NAME));
				sequence.setDialect(getDialect());
				sequence.setSchemaName(getString(rs, SCHEMA_NAME));
				sequence.setStartValue(rs.getBigDecimal("STARTVALUE"));
				sequence.setMinValue(rs.getBigDecimal("MINIMUMVALUE"));
				sequence.setMaxValue(rs.getBigDecimal("MAXIMUMVALUE"));
				sequence.setIncrementBy(rs.getBigDecimal("INCREMENT"));
				sequence.setLastValue(rs.getBigDecimal("CURRENTVALUE"));
				sequence.setCycle("Y".equalsIgnoreCase(
						getString(rs, "CYCLEOPTION")));
				result.add(sequence);
			}
		});
		return result;
	}
}
