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

import java.util.List;

import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Fails for operations Dr.Sum cannot express (FOREIGN KEY and CHECK
 * constraints) and for ALTER TABLE generation, which this dialect does not yet
 * implement, so that generic SQL is never generated for Dr.Sum by mistake.
 */
public class DrSumUnsupportedSqlFactory extends SimpleSqlFactory<Object, AbstractSqlBuilder<?>> {

	@Override
	public List<SqlOperation> createSql(final Object obj) {
		throw new UnsupportedOperationException("Dr.Sum dialect cannot generate this SQL for "
				+ obj.getClass().getSimpleName() + ": " + obj
				+ ". FOREIGN KEY and CHECK constraints do not exist in Dr.Sum; ALTER TABLE generation is not implemented.");
	}
}
