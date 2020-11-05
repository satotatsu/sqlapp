/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.replace;
import static com.sqlapp.util.CommonUtils.trim;
import static com.sqlapp.util.CommonUtils.unwrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SQLServer2005のカラム読み込み
 * 
 * @author satoh
 * 
 */
public class SqlServer2005ColumnReader extends SqlServer2000ColumnReader {

	protected SqlServer2005ColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Column> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Column column = createColumn(rs);
				result.add(column);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("columns2005.sql");
	}

	@Override
	protected Column createColumn(ExResultSet rs) throws SQLException {
		Column column = super.createColumn(rs);
		String check_definition = replace(
				trim(unwrap(getString(rs, "check_definition"), '(', ')')), "["
						+ column.getName() + "]", column.getName());
		String check_constraint_name = getString(rs, "check_constraint_name");
		if (!isEmpty(check_definition)) {
			CheckConstraint checkConstraint = new CheckConstraint(
					check_constraint_name, check_definition, column);
			column.setCheckConstraint(checkConstraint);
		}
		// if (column.getDataType() == Types.UUID) {
		// setDbSpecificInfo(rs, "is_rowguidcol", column);
		// }
		Boolean identityInsert = this.getBoolean(rs, "is_not_for_replication");
		if (identityInsert != null && identityInsert.booleanValue()) {
			setSpecifics(rs, "is_not_for_replication", column);
		}
		setSpecifics(rs, "xmlschema", column);
		return column;
	}
}
