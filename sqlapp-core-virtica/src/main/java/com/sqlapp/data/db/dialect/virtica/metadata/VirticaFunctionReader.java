/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.virtica.metadata;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.FunctionReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionReturning;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.util.TripleKeyMap;

/**
 * Virtica Function Reader
 * 
 * @author satoh
 * 
 */
public class VirticaFunctionReader extends FunctionReader {

	protected VirticaFunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Function> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final TripleKeyMap<String, String, String, Function> map = new TripleKeyMap<String, String, String, Function>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Function function = createFunction(rs);
				map.put(function.getCatalogName(), function.getSchemaName(),
						function.getSpecificName(), function);
			}
		});
		return map.toList();
	}

	protected Function createFunction(ExResultSet rs) throws SQLException {
		Function obj = new Function(getString(rs, FUNCTION_NAME));
		obj.setDefinition(getString(rs, "FUNCTION_DEFINITION"));
		obj.setRemarks(getString(rs, "COMMENT"));
		setReturning(rs, obj);
		String args=getString(rs, "FUNCTION_ARGUMENT_TYPE");
		String[] splits=args.split("\\s*,\\s*");
		for(String split:splits){
			NamedArgument argument=createNamedArgument(split);
			obj.getArguments().add(argument);
		}
		this.setSpecifics(rs, "VOLATILITY", obj);
		this.setSpecifics(rs, "IS_FENCED", obj);
		this.setSpecifics(rs, "VOLATILITY", obj);
		return obj;
	}

	protected void setReturning(ExResultSet rs, Function obj) throws SQLException {
		String data_type = getString(rs, "FUNCTION_RETURN_TYPE");
		FunctionReturning ret = obj.getReturning();
		ret.setDataTypeName(data_type);
		this.getDialect().setDbType(data_type, null, null, ret);
	}

	protected NamedArgument createNamedArgument(String parameter)
			throws SQLException {
		NamedArgument obj = new NamedArgument();
		Matcher matcher=NAMED_ARGUMENT_PATTERN.matcher(parameter);
		if (matcher.matches()){
			obj.setName(matcher.group(1));
			this.getDialect().setDbType(matcher.group(2), null, null, obj);
		}
		return obj;
	}

	private static final Pattern NAMED_ARGUMENT_PATTERN=Pattern.compile("\\s*([^\\s])\\s+(.*)");
	
	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("functions.sql");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.metadata.RoutineReader#newRoutineArgumentReader()
	 */
	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return null;
	}
}
