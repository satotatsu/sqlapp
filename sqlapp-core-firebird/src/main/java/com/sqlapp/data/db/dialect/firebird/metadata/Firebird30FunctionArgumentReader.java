/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static com.sqlapp.util.CommonUtils.trim;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Firebird 3.0以降のPSQL関数引数Readerです。 */
public class Firebird30FunctionArgumentReader extends FirebirdFunctionArgumentReader {

	protected Firebird30FunctionArgumentReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(com.sqlapp.data.schemas.ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("functionArguments30.sql");
	}

	@Override
	protected NamedArgument createNamedArgument(ExResultSet rs) throws SQLException {
		NamedArgument obj = super.createNamedArgument(rs);
		obj.setName(trim(getString(rs, "RDB$ARGUMENT_NAME")));
		if (rs.getInt("RDB$ARGUMENT_POSITION") > 0) {
			obj.setDirection(ParameterDirection.Input);
		}
		return obj;
	}
}
