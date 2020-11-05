/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.hsql.sql;


import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;

public class HsqlSqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public HsqlSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Table.class, SqlType.CREATE,
				HsqlCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER,
				HsqlAlterTableFactory.class);
		registerSqlFactory(Procedure.class, SqlType.CREATE,
				HsqlCreateProcedureFactory.class);
		registerSqlFactory(Function.class, SqlType.CREATE,
				HsqlCreateFunctionFactory.class);
		//
		registerSqlFactory(Sequence.class, SqlType.CREATE,
				HsqlCreateSequenceFactory.class);
	}
}