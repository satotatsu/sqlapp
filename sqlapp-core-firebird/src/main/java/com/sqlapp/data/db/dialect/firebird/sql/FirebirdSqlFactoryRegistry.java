/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;

public class FirebirdSqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public FirebirdSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		// Table
		registerSqlFactory(Table.class, SqlType.CREATE, FirebirdCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.LOCK, FirebirdLockTableFactory.class);
		//
		registerSqlFactory(Sequence.class, SqlType.CREATE, FirebirdCreateSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.ALTER, FirebirdAlterSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.DROP, FirebirdDropSequenceFactory.class);
		//
		registerSqlFactory(Trigger.class, SqlType.CREATE, FirebirdCreateTriggerFactory.class);
	}
}
