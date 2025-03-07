/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.UniqueConstraint;

public class MySqlSqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public MySqlSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	/**
	 * Table,View,Mview以外のSQLコマンドを登録します
	 */
	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		// Schema
		registerSqlFactory(Schema.class, SqlType.CREATE,
				MySqlCreateSchemaFactory.class);
		registerSqlFactory(Schema.class, SqlType.ALTER,
				MySqlAlterSchemaFactory.class);
		registerSqlFactory(Schema.class, SqlType.SET_SEARCH_PATH_TO_SCHEMA,
				MySqlSetSearchPathToSchemaFactory.class);
		//Function
		registerSqlFactory(Function.class, SqlType.CREATE,
				MySqlCreateFunctionFactory.class);
		//Procedure
		registerSqlFactory(Procedure.class, SqlType.CREATE,
				MySqlCreateProcedureFactory.class);
		//Table
		registerSqlFactory(Table.class, SqlType.CREATE,
				MySqlCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER,
				MySqlAlterTableFactory.class);
		registerSqlFactory(Table.class, SqlType.DROP,
				MySqlDropTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ANALYZE,
				MySqlAnalyzeTableFactory.class);
		registerSqlFactory(Table.class, SqlType.OPTIMIZE,
				MySqlAnalyzeTableFactory.class);
		registerSqlFactory(Table.class, SqlType.MERGE_BY_PK,
				MySqlMergeByPkTableFactory.class);
		registerSqlFactory(Table.class, SqlType.LOCK,
				MySqlLockTableFactory.class);
		//UniqueConstraint
		registerSqlFactory(UniqueConstraint.class, SqlType.CREATE,
				MySqlCreateUniqueConstraintFactory.class);
		//ForeignKeyConstraint
		registerSqlFactory(ForeignKeyConstraint.class, SqlType.CREATE,
				MySqlForeignKeyConstraintFactory.class);
		//Trigger
		registerSqlFactory(Trigger.class, SqlType.CREATE,
				MySqlCreateTriggerFactory.class);
		registerSqlFactory(Trigger.class, SqlType.DROP,
				MySqlDropTriggerFactory.class);
		//Index
		registerSqlFactory(Index.class, SqlType.CREATE,
				MySqlCreateIndexFactory.class);
		//Partitioning
		registerSqlFactory(Partitioning.class, SqlType.CREATE,
				MySqlCreatePartitioningFactory.class);
		//
		registerSqlFactory(Event.class, SqlType.CREATE,
				MySqlCreateEventFactory.class);
		// Row
		registerRowSqlFactory(SqlType.MERGE_ROW, MySqlMergeRowFactory.class);
		// Row
		registerRowSqlFactory(SqlType.INSERT_ROW, MySqlInsertRowFactory.class);
	}
}
