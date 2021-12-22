/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;

public class SqlServer2012SqlFactoryRegistry extends
		SqlServer2008SqlFactoryRegistry {

	public SqlServer2012SqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		//Table
		registerSqlFactory(Table.class, SqlType.CREATE,
				SqlServer2012CreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER,
				SqlServer2012AlterTableFactory.class);
		//Index
		registerSqlFactory(Index.class, SqlType.CREATE,
				SqlServer2014CreateIndexFactory.class);
		//Sequence
		registerSqlFactory(Sequence.class, SqlType.CREATE,
				SqlServer2012CreateSequenceFactory.class);
	}

}
