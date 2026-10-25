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

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.trim;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.FunctionReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class FirebirdFunctionReader extends FunctionReader {

	protected FirebirdFunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Function> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Function> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Function function = createFunction(rs);
				result.add(function);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache(FirebirdFunctionReader.class).getString("functions.sql");
	}

	protected Function createFunction(ExResultSet rs) throws SQLException {
		String name = trim(getString(rs, "RDB$FUNCTION_NAME"));
		Function function = new Function(name);
		function.setRemarks(getString(rs, "RDB$DESCRIPTION"));
		String module = trim(getString(rs, "RDB$MODULE_NAME"));
		if (module != null) {
			function.getSpecifics().put("firebird.udf.module", module);
			function.getSpecifics().put("firebird.udf.entrypoint", trim(getString(rs, "RDB$ENTRYPOINT")));
			function.getSpecifics().put("firebird.udf.returnPosition",
					String.valueOf(rs.getInt("RDB$RETURN_ARGUMENT")));
		}
		return function;
	}

	@Override
	protected void setMetadataDetail(Connection connection, ParametersContext context, List<Function> functions)
			throws SQLException {
		FirebirdFunctionArgumentReader reader = (FirebirdFunctionArgumentReader) newRoutineArgumentReader();
		reader.setCatalogName(this.getCatalogName());
		reader.setSchemaName(this.getSchemaName());
		initializeChild(reader);
		reader.setObjectName(null);
		reader.setReadDbObjectPredicate((obj, metadataReader) -> true);
		var arguments = FirebirdRoutineArguments.read(connection, context, functions, reader);
		for (Function function : functions) {
			function.setDialect(this.getDialect());
			for (NamedArgument argument : arguments.getOrDefault(function.getName(), java.util.List.of())) {
				if (function.getName().equals(argument.getRoutineName())) {
					boolean legacyReturn = argument.getSpecifics().get("firebird.udf.position") != null
							&& argument.getSpecifics().get("firebird.udf.position")
									.equals(function.getSpecifics().get("firebird.udf.returnPosition"));
					if (argument.getName() == null || legacyReturn) {
						function.getReturning().setDataType(argument.getDataType())
								.setDataTypeName(argument.getDataTypeName()).setLength(argument.getLength())
								.setScale(argument.getScale()).setCharacterSet(argument.getCharacterSet())
								.setCollation(argument.getCollation());
						if (argument.isNotNull())
							function.getReturning().getSpecifics().put("firebird.notNull", "true");
						if (legacyReturn)
							function.getSpecifics().put("firebird.udf.returnMechanism",
									argument.getSpecifics().get("firebird.udf.mechanism"));
					}
					if (argument.getName() != null) {
						function.getArguments().add(argument);
					}
				}
			}
			if (getReaderOptions().isReadStatement() && function.getSpecifics().containsKey("firebird.udf.module")) {
				var operations = getDialect().createSqlFactoryRegistry().createSql(function,
						com.sqlapp.data.db.sql.SqlType.CREATE);
				function.setDefinition(operations.get(0).getSqlText());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.metadata.RoutineReader#newRoutineArgumentReader()
	 */
	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new FirebirdFunctionArgumentReader(this.getDialect());
	}
}
