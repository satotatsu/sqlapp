/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Table;

public class SapHanaSqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public SapHanaSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Table.class, SqlType.CREATE,
				SapHanaCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.LOCK,
				SapHanaLockTableFactory.class);
		registerSqlFactory(SqlType.DDL_AUTOCOMMIT_OFF,
				SapHanaDdlAutoCommitOffFactory.class);
		registerSqlFactory(SqlType.DDL_AUTOCOMMIT_ON,
				SapHanaDdlAutoCommitOnFactory.class);
		//
		registerSqlFactory(Partition.class, SqlType.TRUNCATE,
				SapHanaTruncatePartitionFactory.class);
	}
}
