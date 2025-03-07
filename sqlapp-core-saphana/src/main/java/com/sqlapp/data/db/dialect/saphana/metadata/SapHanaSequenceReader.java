/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana.metadata;

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
 * SAP HANA Sequence Reader
 * 
 * @author satoh
 * 
 */
public class SapHanaSequenceReader extends SequenceReader {

	protected SapHanaSequenceReader(Dialect dialect) {
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
		Sequence sequence = new Sequence(getString(rs, SEQUENCE_NAME));
		sequence.setSchemaName(getString(rs, SCHEMA_NAME));
		sequence.setId("" + rs.getLong("SEQUENCE_OID"));
		sequence.setMinValue(rs.getBigDecimal("MIN_VALUE"));
		sequence.setMaxValue(rs.getBigDecimal("MAX_VALUE"));
		sequence.setStartValue(rs.getLong("START_NUMBER"));
		sequence.setIncrementBy(rs.getBigDecimal("INCREMENT_BY"));
		sequence.setCycle("TRUE".equalsIgnoreCase(rs.getString("IS_CYCLED")));
		setSpecifics(rs, "RESET_BY_QUERY", sequence);
		return sequence;
	}
}
