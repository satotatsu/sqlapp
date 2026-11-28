/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

public class Firebird30ColumnReader extends FirebirdColumnReader {

	protected Firebird30ColumnReader(Dialect dialect) {
		super(dialect);
	}

	protected Column createColumn(ExResultSet rs) throws SQLException {
		Column obj = super.createColumn(rs);
		Integer identityType = getInteger(rs, "IDENTITY_TYPE");
		if (identityType != null) {
			obj.setIdentity(true);
			obj.setIdentityGenerationType(
					identityType == 0 ? IdentityGenerationType.Always : IdentityGenerationType.ByDefault);
			obj.setSequenceName(CommonUtils.trim(getString(rs, "GENERATOR_NAME")));
			obj.setIdentityStartValue(rs.getLong("INITIAL_VALUE"));
			obj.setIdentityStep(rs.getLong("GENERATOR_INCREMENT"));
		}
		return obj;

	}

	@Override
	protected java.util.List<Column> doGetAll(java.sql.Connection connection,
			com.sqlapp.data.parameter.ParametersContext context, ProductVersionInfo productVersionInfo) {
		java.util.List<Column> result = super.doGetAll(connection, context, productVersionInfo);
		readIdentityNextValues(connection, context, result);
		return result;
	}

	/**
	 * GEN_ID(g, 0) is the generator's current value on every Firebird version (3.0
	 * starts the generator at START WITH, 4.0 one step below it), so current +
	 * increment is the next value to issue. GEN_ID needs a literal generator name,
	 * hence one projection per identity column. Generators the reader may not use
	 * are left unknown rather than failing the whole read.
	 */
	private void readIdentityNextValues(java.sql.Connection connection,
			com.sqlapp.data.parameter.ParametersContext context, java.util.List<Column> columns) {
		java.util.List<Column> identities = new java.util.ArrayList<>();
		for (Column column : columns) {
			if (column.isIdentity() && column.getSequenceName() != null)
				identities.add(column);
		}
		for (int offset = 0; offset < identities.size(); offset += 500) {
			var batch = identities.subList(offset, Math.min(offset + 500, identities.size()));
			StringBuilder sql = new StringBuilder("SELECT ");
			for (int i = 0; i < batch.size(); i++) {
				if (i > 0)
					sql.append(',');
				sql.append("GEN_ID(\"").append(batch.get(i).getSequenceName().replace("\"", "\"\""))
						.append("\",0) AS VALUE_").append(i);
			}
			sql.append(" FROM RDB$DATABASE");
			try {
				execute(connection, com.sqlapp.jdbc.sql.SqlParser.getInstance().parse(getDialect(), sql.toString()),
						context, new com.sqlapp.jdbc.sql.ResultSetNextHandler() {
							@Override
							public void handleResultSetNext(ExResultSet rs) throws SQLException {
								for (int i = 0; i < batch.size(); i++) {
									Column column = batch.get(i);
									Long step = column.getIdentityStep();
									java.math.BigDecimal next = com.sqlapp.data.db.metadata.SequenceNextValues.after(
											rs.getBigDecimal("VALUE_" + i),
											java.math.BigDecimal.valueOf(step == null ? 1L : step), null, null, false);
									if (next != null)
										column.setIdentityLastValue(next.longValueExact());
								}
							}
						});
			} catch (RuntimeException e) {
				// Missing USAGE on a generator: keep the next values unknown.
			}
		}
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("columns30.sql");
	}

}
