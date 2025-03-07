/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.TableSpace;
import com.sqlapp.jdbc.ExResultSet;
/**
 * Postgres9.0以降のテーブルスペース読み込みクラス
 * @author satoh
 *
 */
public class Postgres90TableSpaceReader extends PostgresTableSpaceReader{

	protected Postgres90TableSpaceReader(Dialect dialect) {
		super(dialect);
	}
	
	@Override
	protected TableSpace createTableSpace(ExResultSet rs) throws SQLException{
		TableSpace tableSpace=super.createTableSpace(rs);
		setSpecifics(rs, "spcacl", tableSpace);
		setSpecifics(rs, "spcoptions", tableSpace);
		return tableSpace;
	}
}
