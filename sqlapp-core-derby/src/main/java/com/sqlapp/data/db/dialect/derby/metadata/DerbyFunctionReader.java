/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-derby.
 *
 * sqlapp-core-derby is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-derby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-derby.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.derby.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcFunctionReader;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.jdbc.ExResultSet;

public class DerbyFunctionReader extends JdbcFunctionReader{

	public DerbyFunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Function createFunction(ExResultSet rs) throws SQLException{
		String name=getString(rs, FUNCTION_NAME);
		String specificName=getString(rs, SPECIFIC_NAME);
		String remarks=getString(rs, "REMARKS");
		int pos=remarks.lastIndexOf('.');
		Function function=new Function(name, specificName);
		function.setCatalogName(getString(rs, "FUNCTION_CAT"));
		function.setSchemaName(getString(rs, "FUNCTION_SCHEM"));
		function.setClassName(remarks.substring(0, pos));
		function.setMethodName(remarks.substring(pos + 1));
		return function;
	}
}
