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
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.jdbc.ExResultSet;

/** Firebird 3.0以降のPSQL関数Readerです。 */
public class Firebird30FunctionReader extends FirebirdFunctionReader {

	protected Firebird30FunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Function createFunction(ExResultSet rs) throws SQLException {
		Function obj = super.createFunction(rs);
		if (this.getReaderOptions().isReadStatement()) {
			obj.setStatement(trim(getString(rs, "RDB$FUNCTION_SOURCE")));
		}
		return obj;
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new Firebird30FunctionArgumentReader(this.getDialect());
	}
}
