/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.metadata;


import com.sqlapp.jdbc.ExResultSet;
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;

/**
 * DB2 10.5.0 Column Reader
 * 
 * @author satoh
 * 
 */
public class Db2_1050ColumnReader extends Db2ColumnReader {

	protected Db2_1050ColumnReader(Dialect dialect) {
		super(dialect);
	}


	protected Column createColumn(ExResultSet rs) throws SQLException {
		Column obj = super.createColumn(rs);
		String stringUnits=getString(rs, "TYPESTRINGUNITS");
		if (stringUnits!=null){
			obj.setStringUnits(stringUnits);
			Integer stringunitLength=getInteger(rs, "STRINGUNITSLENGTH");
			if (stringunitLength!=null){
				obj.setLength(stringunitLength);
				this.getDialect().setDbType(obj.getDataTypeName(), stringunitLength.longValue(), null, obj);
			}
		}
		return obj;
	}

}
