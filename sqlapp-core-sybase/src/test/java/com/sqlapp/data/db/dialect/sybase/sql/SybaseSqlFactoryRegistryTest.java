/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sybase.
 *
 * sqlapp-core-sybase is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sybase is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sybase.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sybase.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;

/**
 * Sql Server用のコマンドファクトリテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class SybaseSqlFactoryRegistryTest {
	SqlFactoryRegistry sqlFactoryRegistry;

	@BeforeEach
	public void before() {
		Dialect dialect = DialectResolver.getInstance().getDialect("sybase", 7, 0);
		sqlFactoryRegistry = dialect.getSqlFactoryRegistry();
	}

	@Test
	public void testGetDbOperation() {
		Table table = new Table();
		SqlFactory<?> sqlFactory = sqlFactoryRegistry
				.getSqlFactory(table.newRow(), State.Added);
		assertTrue(sqlFactory instanceof SybaseInsertRowFactory);
	}
}
