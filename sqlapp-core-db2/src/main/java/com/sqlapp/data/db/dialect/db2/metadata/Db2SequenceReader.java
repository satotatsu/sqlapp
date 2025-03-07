/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.math.BigDecimal;
import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SequenceReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Sequence;

/**
 * DB2のシーケンスファクトリ
 * 
 * @author satoh
 * 
 */
public class Db2SequenceReader extends SequenceReader {

	protected Db2SequenceReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Sequence> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Sequence> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Sequence sequence = createSequence(rs);
				result.add(sequence);
			}
		});
		return result;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("sequences.sql");
	}

	protected Sequence createSequence(ExResultSet rs) throws SQLException {
		String cycle = getString(rs, "CYCLE");// Y,N
		String order = getString(rs, "ORDER");// Y,N
		BigDecimal cacheSize = rs.getBigDecimal("CACHE");
		Sequence sequence = new Sequence(getString(rs, SEQUENCE_NAME));
		sequence.setSchemaName(getString(rs, SCHEMA_NAME));
		sequence.setStartValue(rs.getBigDecimal("START"));
		sequence.setMinValue(rs.getBigDecimal("MINVALUE"));
		sequence.setMaxValue(rs.getBigDecimal("MAXVALUE"));
		sequence.setIncrementBy(rs.getBigDecimal("INCREMENT"));
		sequence.setCacheSize(cacheSize);
		sequence.setLastValue(rs.getBigDecimal("NEXTCACHEFIRSTVALUE"));
		sequence.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		sequence.setLastAlteredAt(rs.getTimestamp("ALTER_TIME"));
		sequence.setRemarks(getString(rs, REMARKS));
		int precision = rs.getInt("PRECISION");
		switch (precision) {
		case 5:
			sequence.setDataType(DataType.SMALLINT);
			break;
		case 10:
			sequence.setDataType(DataType.INT);
			break;
		case 19:
			sequence.setDataType(DataType.BIGINT);
			break;
		default:
			sequence.setDataType(DataType.DECIMAL);
			sequence.setPrecision(precision);
		}
		if ("Y".equalsIgnoreCase(cycle)) {
			sequence.setCycle(true);
		} else {
			sequence.setCycle(false);
		}
		if ("Y".equalsIgnoreCase(order)) {
			sequence.setOrder(true);
		} else {
			sequence.setOrder(false);
		}
		return sequence;
	}
}
