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

package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;

public class Db2SqlFactoryRegistry extends SimpleSqlFactoryRegistry {


	public Db2SqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		//Table
		registerSqlFactory(Table.class, SqlType.CREATE,
				Db2CreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE,
				Db2TruncateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.LOCK,
				Db2LockTableFactory.class);
		//Sequence
		registerSqlFactory(Sequence.class, SqlType.CREATE,
				Db2CreateSequenceFactory.class);
		//Procedure
		registerSqlFactory(Procedure.class, SqlType.CREATE,
				Db2CreateProcedureFactory.class);
		//Partitioning
		registerSqlFactory(Partitioning.class, SqlType.CREATE,
				Db2CreatePartitioningFactory.class);
		//Row
		registerRowSqlFactory(SqlType.INSERT_ROW, Db2InsertRowFactory.class);
		registerRowSqlFactory(SqlType.MERGE_ROW, Db2MergeRowFactory.class);
	}
}
