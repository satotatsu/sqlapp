/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-phoenix.
 *
 * sqlapp-core-phoenix is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-phoenix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-phoenix.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.phoenix.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;

public class PhoenixSqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public PhoenixSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Table.class, SqlType.INSERT, PhoenixUpsertFactories.Single.class);
		registerSqlFactory(com.sqlapp.data.schemas.Row.class, SqlType.INSERT, PhoenixUpsertFactories.AddedRow.class);
		registerSqlFactory(com.sqlapp.data.schemas.Row.class, SqlType.DELETE, PhoenixRowDeleteFactory.class);
		registerSqlFactory(Table.class, SqlType.INSERT_ROWS, PhoenixUpsertFactories.Rows.class);
		registerSqlFactory(Table.class, SqlType.INSERT_SELECT_NOT_EXISTS, PhoenixUpsertFactories.SelectNotExists.class);
		registerSqlFactory(Table.class, SqlType.SELECT_FOR_APP, PhoenixSelectForAppFactory.class);
		for (var type : java.util.List.of(SqlType.UPDATE, SqlType.UPDATE_TABLE, SqlType.UPDATE_FOR_APP, SqlType.MERGE,
				SqlType.MERGE_ROWS, SqlType.MERGE_TABLE)) {
			for (Class<?> objectType : new Class<?>[] { Table.class, com.sqlapp.data.schemas.Row.class,
					com.sqlapp.data.schemas.RowCollection.class })
				registerSqlFactory(objectType, type, PhoenixUnsupportedDmlFactory.class);
		}
		registerSqlFactory(com.sqlapp.data.schemas.View.class, SqlType.CREATE, PhoenixCreateViewFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.View.class, SqlType.DROP, PhoenixDropViewFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.View.class, SqlType.ALTER, PhoenixAlterViewFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE, PhoenixCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER, PhoenixAlterTableFactory.class);
		registerSqlFactory(Sequence.class, SqlType.CREATE, PhoenixCreateSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.ALTER, PhoenixAlterSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.DROP, PhoenixDropSequenceFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Index.class, SqlType.CREATE, PhoenixCreateIndexFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Index.class, SqlType.DROP, PhoenixDropIndexFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Index.class, SqlType.REBUILD, PhoenixRebuildIndexFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Index.class, SqlType.ALTER, PhoenixAlterIndexFactory.class);
		registerSqlFactory(Table.class, SqlType.LOCK, PhoenixLockTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE, PhoenixTruncateTableFactory.class);
		registerSqlFactory(Sequence.class, SqlType.SEQUENCE_NEXT_VALUES, PhoenixSequenceNextValuesFactory.class);
	}
}
