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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.OperatorReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Operator;
import com.sqlapp.data.schemas.OperatorBinding;
import com.sqlapp.data.schemas.OperatorBindingArgument;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;
import com.sqlapp.util.TripleKeyMap;

/**
 * OracleのOperator作成クラス
 * 
 * @author satoh
 * 
 */
public class OracleOperatorReader extends OperatorReader {

	protected OracleOperatorReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Operator> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final DoubleKeyMap<String, String, Operator> map = CommonUtils.doubleKeyMap();
		final TripleKeyMap<String, String, Integer, OperatorBinding> bindingMap = CommonUtils.tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schema_name = getString(rs, "OWNER");
				String name = getString(rs, OPERATOR_NAME);
				Integer bindingNo = getInteger(rs, "BINDING#");
				Operator operator = map.get(schema_name, name);
				if (operator == null) {
					operator = createOperator(rs);
					map.put(schema_name, name, operator);
				}
				OperatorBinding binding=createOperatorBinding(rs);
				operator.getBindings().add(binding);
				bindingMap.put(operator.getSchemaName(), operator.getName(), bindingNo, binding);
			}
		});
		setArgument(connection, context, bindingMap);
		return map.toList();
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("operators.sql");
	}

	protected OperatorBinding createOperatorBinding(ExResultSet rs) throws SQLException {
		OperatorBinding obj=new OperatorBinding();
		obj.setDialect(this.getDialect());
		obj.setTypeSchemaName(getString(rs, "RETURN_SCHEMA"));
		obj.setDataTypeName(getString(rs, "RETURN_TYPE"));
		if (obj.getImplementationType() != null) {
			obj.getImplementationType().setSchemaName(
					getString(rs, "IMPLEMENTATION_TYPE_SCHEMA"));
		}
		obj.setImplementationTypeName(getString(rs, "IMPLEMENTATION_TYPE"));
		obj.setProperty(getString(rs, "PROPERTY"));
		return obj;
	}
	
	
	protected Operator createOperator(ExResultSet rs) throws SQLException {
		Operator obj = new Operator(getString(rs, OPERATOR_NAME));
		obj.setSchemaName(getString(rs, "OWNER"));
		obj.setFunctionName(getString(rs, "FUNCTION_NAME").replaceAll("\"", ""));
		obj.setRemarks(getString(rs, "COMMENTS"));
		return obj;
	}

	protected void setArgument(final Connection connection,
			final ParametersContext context,
			final TripleKeyMap<String, String, Integer, OperatorBinding> bindingMap) {
		SqlNode node = getSqlNodeCache().getString("operatorArguments.sql");
		ParametersContext copyContext=context.clone();
		copyContext.put(SCHEMA_NAME, bindingMap.keySet());
		copyContext.put(OPERATOR_NAME, bindingMap.secondKeySet());
		execute(connection, node, copyContext, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schema_name = getString(rs, "OWNER");
				String name = getString(rs, OPERATOR_NAME);
				Integer bindingNo = getInteger(rs, "BINDING#");
				OperatorBinding binding = bindingMap.get(schema_name, name, bindingNo);
				if (binding == null) {
					return;
				}
				OperatorBindingArgument arg = createOperatorBindingArgument(rs);
				binding.getArguments().add(arg);
			}
		});
	}

	protected OperatorBindingArgument createOperatorBindingArgument(ExResultSet rs)
			throws SQLException {
		OperatorBindingArgument arg = new OperatorBindingArgument();
		arg.setDialect(this.getDialect());
		arg.setDataTypeName(getString(rs, "ARGUMENT_TYPE"));
		return arg;
	}
}
