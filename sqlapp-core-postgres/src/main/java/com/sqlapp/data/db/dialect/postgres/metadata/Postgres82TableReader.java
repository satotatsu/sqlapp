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

import java.sql.SQLException;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;

/**
 * Postgres8.2 Table Reader
 * 
 * @author satoh
 * 
 */
public class Postgres82TableReader extends PostgresTableReader {

	protected Postgres82TableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Table createTable(ExResultSet rs) throws SQLException {
		Table obj = super.createTable(rs);
		obj.setTableSpaceName(getString(rs, "spcname"));
		String reloptions=getString(rs, "reloptions");
		Map<String,String> map=PostgresUtils.parseRelOption(reloptions);
		map.forEach((k,v)->{
			obj.getSpecifics().put(k, v);
		});
		return obj;
	}


}
