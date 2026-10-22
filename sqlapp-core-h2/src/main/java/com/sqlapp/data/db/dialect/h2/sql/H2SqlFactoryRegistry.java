/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.View;

public class H2SqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public H2SqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Table.class, SqlType.CREATE, H2CreateTableFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Index.class, SqlType.CREATE, H2CreateIndexFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER, H2AlterTableFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE_TEMPORARY, H2CreateTemporaryTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE_TEMPORARY, H2TruncateTemporaryTableFactory.class);
		registerSqlFactory(Table.class, SqlType.DROP, H2DropTableFactory.class);
		registerSqlFactory(View.class, SqlType.DROP, H2DropViewFactory.class);
		registerSqlFactory(View.class, SqlType.CREATE, H2CreateViewFactory.class);
		registerSqlFactory(Trigger.class, SqlType.CREATE, H2CreateTriggerFactory.class);
		registerSqlFactory(Function.class, SqlType.CREATE, H2CreateFunctionFactory.class);
		registerSqlFactory(Function.class, SqlType.DROP, H2DropFunctionFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Constant.class, SqlType.CREATE, H2CreateConstantFactory.class);
		//
		registerSqlFactory(Sequence.class, SqlType.CREATE, H2CreateSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.SEQUENCE_NEXT_VALUES, H2SequenceNextValuesFactory.class);
		registerSqlFactory(Domain.class, SqlType.CREATE, H2CreateDomainFactory.class);
		registerSqlFactory(Domain.class, SqlType.DROP, H2DropDomainFactory.class);
		registerSqlFactory(Table.class, SqlType.LOCK, H2LockTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ANALYZE, H2AnalyzeTableFactory.class);
		for (Class<?> type : java.util.List.<Class<?>>of(Table.class, View.class, com.sqlapp.data.schemas.Column.class,
				com.sqlapp.data.schemas.Index.class, Domain.class, Sequence.class, Function.class, Trigger.class,
				com.sqlapp.data.schemas.Constant.class, com.sqlapp.data.schemas.Schema.class))
			registerSqlFactory(type, SqlType.SET_COMMENT, H2SetCommentFactory.class);
		registerSqlFactory(Table.class, SqlType.INSERT, H2InsertFactory.class);
		registerSqlFactory(Table.class, SqlType.INSERT_ROWS, H2InsertFactory.Rows.class);
		if (getDialect() instanceof com.sqlapp.data.db.dialect.h2.H2_200) {
			registerSqlFactory(com.sqlapp.data.schemas.Synonym.class, SqlType.CREATE, H2CreateSynonymFactory.class);
			registerSqlFactory(Table.class, SqlType.MERGE, H2MergeFactory.class);
			registerSqlFactory(Table.class, SqlType.MERGE_ROWS, H2MergeFactory.Rows.class);
			registerSqlFactory(Table.class, SqlType.MERGE_TABLE, H2MergeFactory.Staged.class);
		}
	}
}
