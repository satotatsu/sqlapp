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

package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Operator;
import com.sqlapp.data.schemas.OperatorClass;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.View;

public class PostgresSqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public PostgresSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.operation.SimpleDbOperationRegistry#initializeAllStateOperation()
	 */
	@Override
	protected void initializeAllStateSqls() {
		super.initializeAllStateSqls();
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.operation.SimpleDbOperationRegistry#initializeAllSqlOperation()
	 */
	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		//Table
		registerSqlFactory(Table.class, SqlType.CREATE,
				PostgresCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER,
				PostgresAlterTableFactory.class);
		registerSqlFactory(Table.class, SqlType.LOCK,
				PostgresLockTableFactory.class);
		//View
		registerSqlFactory(View.class, SqlType.CREATE,
				PostgresCreateViewFactory.class);
		//Index
		registerSqlFactory(Index.class, SqlType.CREATE,
				PostgresCreateIndexFactory.class);
		//CheckConstraint
		registerSqlFactory(CheckConstraint.class, SqlType.CREATE,
				PostgresCreateCheckConstraintFactory.class);
		//UniqueConstraint
		registerSqlFactory(UniqueConstraint.class, SqlType.CREATE,
				PostgresCreateUniqueConstraintFactory.class);
		//ForeignKeyConstraint
		registerSqlFactory(ForeignKeyConstraint.class, SqlType.CREATE,
				PostgresCreateForeignKeyConstraintFactory.class);
		//Trigger
		registerSqlFactory(Trigger.class, SqlType.CREATE,
				PostgresCreateTriggerFactory.class);
		//Operator
		registerSqlFactory(Operator.class, SqlType.CREATE,
				PostgresCreateOperatorFactory.class);
		//OperatorClass
		registerSqlFactory(OperatorClass.class, SqlType.CREATE,
				PostgresCreateOperatorClassFactory.class);
		//
		registerSqlFactory(Schema.class, SqlType.SET_SEARCH_PATH_TO_SCHEMA,
				PostgresSetSearchPathToSchemaFactory.class);
		//
		registerSqlFactory(Sequence.class, SqlType.CREATE,
				PostgresCreateSequenceFactory.class);
		//
		registerSqlFactory(Function.class, SqlType.CREATE,
				PostgresCreateFunctionFactory.class);
		// Row
		registerRowSqlFactory(SqlType.INSERT_ROW, PostgresInsertRowFactory.class);
	}
}
