/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SequenceReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * HSQLのシーケンスファクトリ
 * 
 * @author satoh
 * 
 */
public class HsqlSequenceReader extends SequenceReader {

	protected HsqlSequenceReader(Dialect dialect) {
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
		// String order=getString(rs, "ORDER_FLAG");//Y,N
		// BigDecimal cacheSize=rs.getBigDecimal("CACHE_SIZE");
		Sequence sequence = new Sequence(getString(rs, SEQUENCE_NAME));
		sequence.setDialect(this.getDialect());
		sequence.setCatalogName(getString(rs, "SEQUENCE_CATALOG"));
		sequence.setSchemaName(getString(rs, "SEQUENCE_SCHEMA"));
		sequence.setMinValue(rs.getBigDecimal("MINIMUM_VALUE"));
		sequence.setMaxValue(rs.getBigDecimal("MAXIMUM_VALUE"));
		sequence.setIncrementBy(rs.getBigDecimal("INCREMENT"));
		sequence.setStartValue(rs.getBigDecimal("START_WITH"));
		sequence.setLastValue(rs.getBigDecimal("NEXT_VALUE"));
		sequence.setDataTypeName(getString(rs, "DATA_TYPE"));
		if (sequence.getDataType() == DataType.DECIMAL
				|| sequence.getDataType() == DataType.NUMERIC) {
			sequence.setPrecision(rs.getInt("NUMERIC_PRECISION"));
		}
		String cycle = getString(rs, "CYCLE_OPTION");// Y,N
		if ("Y".equalsIgnoreCase(cycle)) {
			sequence.setCycle(true);
		} else {
			sequence.setCycle(false);
		}
		return sequence;
	}
}
