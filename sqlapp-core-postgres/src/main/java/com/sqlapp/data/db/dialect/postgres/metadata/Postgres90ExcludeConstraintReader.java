/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.postgres.metadata;

import static com.sqlapp.util.CommonUtils.first;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.map;
import static com.sqlapp.util.CommonUtils.split;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;
import static com.sqlapp.util.CommonUtils.unwrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.OperatorReader;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.schemas.ExcludeConstraint;
import com.sqlapp.data.schemas.Operator;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;

/**
 * PostgresのExclude制約読み込みクラス
 * 
 * @author satoh
 * 
 */
public class Postgres90ExcludeConstraintReader extends ExcludeConstraintReader {

	public Postgres90ExcludeConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ExcludeConstraint> doGetAll(final Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<ExcludeConstraint> result = list();
		final TripleKeyMap<String, String, String, ExcludeConstraint> map = tripleKeyMap();
		final Map<String, Operator> operatorMap = map();
		final TripleKeyMap<String, String, String, String[]> operatorIdMap = tripleKeyMap();
		TableReader tableReader = this.getParent();
		SchemaReader schemaReader = tableReader.getParent();
		final OperatorReader operatorReader = schemaReader.getOperatorReader();
		operatorReader.setCatalogName(null);
		operatorReader.setSchemaName(null);
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schema_name = getString(rs, "constraint_schema");
				String table_name = getString(rs, TABLE_NAME);
				String constraint_name = getString(rs, CONSTRAINT_NAME);
				ExcludeConstraint c = map.get(schema_name, table_name,
						constraint_name);
				if (c == null) {
					c = new ExcludeConstraint(constraint_name);
					c.setSchemaName(schema_name);
					c.setTableName(table_name);
					c.setDeferrability(Deferrability.getDeferrability(
							rs.getBoolean("is_deferrable"),
							rs.getBoolean("initially_deferred")));
					result.add(c);
					// conexclop
					String oids = unwrap(rs.getString("conexclop"), "{", "}");
					String[] oidArray = split(oids, "[, ]");
					operatorIdMap.put(schema_name, table_name, constraint_name,
							oidArray);
					for (String oid : oidArray) {
						Operator operator = operatorMap.get(oid);
						if (operator == null) {
							ParametersContext opContext = new ParametersContext();
							opContext.put("id", Integer.valueOf(oid));
							List<Operator> list = operatorReader.getAll(
									connection, opContext);
							operator = first(list);
							operatorMap.put(oid, operator);
						}
					}
					map.put(schema_name, table_name, constraint_name, c);
				}
				int attnum = rs.getInt("attnum");
				String[] oidArray = operatorIdMap.get(schema_name, table_name,
						constraint_name);
				Operator operator = operatorMap.get(oidArray[attnum - 1]);
				Column column = new Column(getString(rs, COLUMN_NAME));
				column.setTableName(table_name);
				c.getColumns().add(column);
				c.getColumns().get(column.getName())
						.setWith(operator.getName());
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("excludeConstraints.sql");
	}

}
