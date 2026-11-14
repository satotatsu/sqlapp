/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-drsum.
 *
 * sqlapp-core-drsum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-drsum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-drsum.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.drsum.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;

/** Dr.Sum SQL factories. */
public class DrSumSqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public DrSumSqlFactoryRegistry(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Table.class, SqlType.CREATE, DrSumCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER, DrSumUnsupportedSqlFactory.class);
		registerSqlFactory(Table.class, SqlType.SET_COMMENT, DrSumSetCommentFactory.class);
		registerSqlFactory(Column.class, SqlType.SET_COMMENT, DrSumSetCommentFactory.class);
		registerSqlFactory(Index.class, SqlType.CREATE, DrSumCreateIndexFactory.class);
		registerSqlFactory(Index.class, SqlType.DROP, DrSumDropIndexFactory.class);
		registerSqlFactory(UniqueConstraint.class, SqlType.CREATE, DrSumCreateUniqueConstraintFactory.class);
		registerSqlFactory(UniqueConstraint.class, SqlType.DROP, DrSumDropUniqueConstraintFactory.class);
		registerSqlFactory(ForeignKeyConstraint.class, SqlType.CREATE, DrSumUnsupportedSqlFactory.class);
		registerSqlFactory(CheckConstraint.class, SqlType.CREATE, DrSumUnsupportedSqlFactory.class);
	}
}
