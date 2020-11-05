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
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcProcedureReader;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.jdbc.ExResultSet;

public class DerbyProcedureReader extends JdbcProcedureReader{

	public DerbyProcedureReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Procedure createProcedure(ExResultSet rs) throws SQLException{
		String name=getString(rs, PROCEDURE_NAME);
		String specificName=getString(rs, SPECIFIC_NAME);
		String remarks=getString(rs, "REMARKS");
		int pos=remarks.lastIndexOf('.');
		Procedure procedure=new Procedure(name, specificName);
		procedure.setCatalogName(getString(rs, "PROCEDURE_CAT"));
		procedure.setSchemaName(getString(rs, "PROCEDURE_SCHEM"));
		procedure.setClassName(remarks.substring(0, pos));
		procedure.setMethodName(remarks.substring(pos + 1));
		return procedure;
	}
}
