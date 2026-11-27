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

package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.DropNamedObjectFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.State;

public class Postgres93SqlFactoryRegistry extends Postgres92SqlFactoryRegistry {

	public Postgres93SqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.operation.SimpleDbOperationRegistry#
	 * initializeAllStateOperation()
	 */
	@Override
	protected void initializeAllStateSqls() {
		super.initializeAllStateSqls();
		registerSqlFactory(Mview.class, State.Modified, SqlType.ALTER);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Mview.class, SqlType.CREATE, Postgres93CreateMviewFactory.class);
		registerSqlFactory(Mview.class, SqlType.ALTER, Postgres93AlterMviewFactory.class);
		registerSqlFactory(Mview.class, SqlType.DROP, Postgres93DropMviewFactory.class);
		registerSqlFactory(Mview.class, SqlType.REFRESH, Postgres93RefreshMviewFactory.class);
		registerSqlFactory(Index.class, SqlType.DROP, DropNamedObjectFactory.class);
	}

}
