/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.View;

public class DropViewFactoryTest {
	SqlFactory<View> command;

	@BeforeEach
	public void before() {
		final Dialect dialect = DialectResolver.getInstance().getDialect("Standard",
				0, 0);
		final Schema schema = new Schema();
		final View view = new View("viewA");
		schema.getViews().add(view);
		command = dialect.createSqlFactoryRegistry().getSqlFactory(view,
				State.Deleted);
	}

	@Test
	public void testGetDdlTable() {
		final View table = new View("viewA");
		final List<SqlOperation> list = command.createSql(table);
		System.out.println(list);
		assertEquals("DROP VIEW \"viewA\"", list.get(0).getSqlText());
	}

}
