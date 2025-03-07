/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

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
 * Oracleのシーケンスファクトリ
 * 
 * @author satoh
 * 
 */
public class OracleSequenceReader extends SequenceReader {

	protected OracleSequenceReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Sequence> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
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

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("sequences.sql");
	}

	protected Sequence createSequence(ExResultSet rs) throws SQLException {
		String cycle = getString(rs, "CYCLE_FLAG");// Y,N
		String order = getString(rs, "ORDER_FLAG");// Y,N
		BigDecimal cacheSize = rs.getBigDecimal("CACHE_SIZE");
		BigDecimal lastNumber = rs.getBigDecimal("LAST_NUMBER");
		Sequence sequence = new Sequence(getString(rs, SEQUENCE_NAME));
		sequence.setSchemaName(getString(rs, "SEQUENCE_OWNER"));
		sequence.setMinValue(rs.getBigDecimal("MIN_VALUE"));
		sequence.setMaxValue(rs.getBigDecimal("MAX_VALUE"));
		sequence.setIncrementBy(rs.getBigDecimal("INCREMENT_BY"));
		sequence.setCacheSize(cacheSize);
		sequence.setLastValue(lastNumber);
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
