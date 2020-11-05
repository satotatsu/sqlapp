/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.information_schema.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.math.BigDecimal;
import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SequenceReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Sequence;

/**
 * INFORMATION_SCHEMAのシーケンス読み込み
 * 
 * @author satoh
 * 
 */
public class ISSequenceReader extends SequenceReader {

	protected ISSequenceReader(Dialect dialect) {
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

	protected Sequence createSequence(ExResultSet rs) throws SQLException {
		String name = getString(rs, "SEQUENCE_NAME");
		BigDecimal cacheSize = rs.getBigDecimal("CACHE_SIZE");
		BigDecimal lastNumber = rs.getBigDecimal("LAST_NUMBER");
		Sequence sequence = new Sequence(name);
		sequence.setCatalogName(getString(rs, "SEQUENCE_CATALOG"));
		sequence.setSchemaName(getString(rs, "SEQUENCE_SCHEMA"));
		sequence.setIncrementBy(rs.getBigDecimal("INCREMENT"));
		sequence.setMinValue(rs.getBigDecimal("MINIMUM_VALUE"));
		sequence.setMaxValue(rs.getBigDecimal("MAXIMUM_VALUE"));
		sequence.setCacheSize(cacheSize);
		sequence.setLastValue(lastNumber);
		return sequence;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache(ISSequenceReader.class).getString(
				"sequences.sql");
		return node;
	}
}
