/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-spanner.
 *
 * sqlapp-core-spanner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-spanner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-spanner.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlite.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;

public class SqliteSqlFactoryRegistry extends SimpleSqlFactoryRegistry {
	private java.util.function.Function<Table, String> mergeConflictIndex = table -> null;

	/**
	 * Selects a modeled unique index for all MERGE forms; null uses shared key
	 * selection.
	 */
	public void setMergeConflictIndexName(String name) {
		mergeConflictIndex = table -> name;
	}

	/**
	 * Optional per-table index selection; a null result uses shared key selection.
	 */
	public void setMergeConflictIndexSelector(java.util.function.Function<Table, String> selector) {
		mergeConflictIndex = java.util.Objects.requireNonNull(selector, "mergeConflictIndex selector");
	}

	@Override
	protected <T, U extends com.sqlapp.data.db.sql.SqlFactory<?>> U initializeSqls(T object,
			com.sqlapp.data.db.sql.SqlFactory<?> factory) {
		U initialized = super.initializeSqls(object, factory);
		if (initialized instanceof SqliteMergeFactory merge)
			merge.setConflictIndexSelector(mergeConflictIndex);
		return initialized;
	}

	public SqliteSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(com.sqlapp.data.schemas.Schema.class, SqlType.CREATE, SqliteCreateSchemaFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Schema.class, SqlType.ALTER, SqliteAlterSchemaFactory.class);
		registerSqlFactory(Table.class, SqlType.REBUILD, SqliteReindexFactory.class);
		registerSqlFactory(Index.class, SqlType.REBUILD, SqliteReindexFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Schema.class, SqlType.REBUILD, SqliteReindexFactory.class);
		registerSqlFactory(Table.class, SqlType.ANALYZE, SqliteAnalyzeFactory.class);
		registerSqlFactory(Index.class, SqlType.ANALYZE, SqliteAnalyzeFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Schema.class, SqlType.ANALYZE, SqliteAnalyzeFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Schema.class, SqlType.DEFRAG, SqliteVacuumFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Schema.class, SqlType.DEFRAG_FULL, SqliteVacuumFactory.Full.class);
		registerSqlFactory(Table.class, SqlType.DEFRAG, SqliteVacuumFactory.class);
		registerSqlFactory(Table.class, SqlType.DEFRAG_FULL, SqliteVacuumFactory.Full.class);
		registerSqlFactory(Index.class, SqlType.DEFRAG, SqliteVacuumFactory.class);
		registerSqlFactory(Index.class, SqlType.DEFRAG_FULL, SqliteVacuumFactory.Full.class);
		registerSqlFactory(Table.class, SqlType.INSERT, SqliteInsertFactory.class);
		registerSqlFactory(Table.class, SqlType.INSERT_ROWS, SqliteInsertFactory.Rows.class);
		registerSqlFactory(Table.class, SqlType.MERGE, SqliteMergeFactory.class);
		registerSqlFactory(Table.class, SqlType.MERGE_ROWS, SqliteMergeFactory.Rows.class);
		registerSqlFactory(Table.class, SqlType.MERGE_TABLE, SqliteMergeTableFactory.class);
		registerSqlFactory(Table.class, SqlType.INSERT_SELECT_TABLE, SqliteInsertSelectTableFactory.class);
		registerSqlFactory(Table.class, SqlType.INSERT_SELECT_NOT_EXISTS, SqliteInsertSelectNotExistsFactory.class);
		registerSqlFactory(Table.class, SqlType.UPDATE, SqliteUpdateFactory.class);
		registerSqlFactory(Table.class, SqlType.UPDATE_TABLE, SqliteUpdateFactory.All.class);
		registerSqlFactory(Table.class, SqlType.UPDATE_FOR_APP, SqliteUpdateFactory.ForApp.class);
		registerSqlFactory(Table.class, SqlType.DELETE, SqliteDeleteFactory.class);
		registerSqlFactory(Table.class, SqlType.DELETE_ALL, SqliteDeleteFactory.All.class);
		registerSqlFactory(Table.class, SqlType.DELETE_FOR_APP, SqliteDeleteFactory.ForApp.class);
		registerSqlFactory(Table.class, SqlType.SELECT_FOR_APP, SqliteSelectForAppFactory.class);
		registerSqlFactory(Table.class, SqlType.SELECT, SqliteSelectFactory.class);
		registerSqlFactory(Table.class, SqlType.SELECT_TABLE, SqliteSelectTableFactory.class);
		registerSqlFactory(Table.class, SqlType.SELECT_BY_ROWS, SqliteSelectFactory.ByRows.class);
		registerSqlFactory(TableRelation.class, SqlType.SELECT_BY_ROOT_ROWS, SqliteSelectFactory.ByRootRows.class);
		registerSqlFactory(TableRelation.class, SqlType.DELETE_BY_ROOT_ROWS, SqliteDeleteFactory.ByRootRows.class);
		registerSqlFactory(Table.class, SqlType.LOCK, SqliteLockTableFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE, SqliteCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER, SqliteAlterTableFactory.class);
		registerSqlFactory(Table.class, SqlType.DROP, SqliteDropTableFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.CheckConstraint.class, SqlType.CREATE,
				SqliteConstraintFactories.CreateCheck.class);
		registerSqlFactory(com.sqlapp.data.schemas.UniqueConstraint.class, SqlType.CREATE,
				SqliteConstraintFactories.CreateUnique.class);
		registerSqlFactory(com.sqlapp.data.schemas.CheckConstraint.class, SqlType.DROP,
				SqliteConstraintFactories.DropCheck.class);
		registerSqlFactory(com.sqlapp.data.schemas.UniqueConstraint.class, SqlType.DROP, SqliteDropObjectFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.ForeignKeyConstraint.class, SqlType.DROP,
				SqliteDropObjectFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.View.class, SqlType.DROP, SqliteDropObjectFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Trigger.class, SqlType.DROP, SqliteDropObjectFactory.class);
		registerSqlFactory(Index.class, SqlType.DROP, SqliteDropObjectFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.ForeignKeyConstraint.class, SqlType.CREATE,
				SqliteCreateForeignKeyConstraintFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.View.class, SqlType.CREATE, SqliteCreateViewFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Trigger.class, SqlType.CREATE, SqliteCreateTriggerFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.View.class, SqlType.ALTER, SqliteAlterSchemaObjectFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Trigger.class, SqlType.ALTER, SqliteAlterSchemaObjectFactory.class);

		registerSqlFactory(Index.class, SqlType.CREATE, SqliteCreateIndexFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE_TEMPORARY, SqliteCreateTemporaryTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE, SqliteTruncateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE_TEMPORARY, SqliteTruncateTemporaryTableFactory.class);
	}

	@Override
	protected void initializeAllStateSqls() {
		super.initializeAllStateSqls();
		registerSqlFactory(com.sqlapp.data.schemas.View.class, com.sqlapp.data.schemas.State.Modified, SqlType.ALTER);
		registerSqlFactory(com.sqlapp.data.schemas.Trigger.class, com.sqlapp.data.schemas.State.Modified,
				SqlType.ALTER);
	}

}
