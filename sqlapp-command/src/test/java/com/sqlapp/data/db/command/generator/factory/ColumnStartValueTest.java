/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.generator.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class ColumnStartValueTest {

	private ColumnStartValue func = new ColumnStartValue();

	private Dialect dialect = DialectResolver.getInstance().getDialect("Default", 0, 0);

	@Test
	void testNumber() {
		Column column = new Column("COLA");
		column.setDataType(DataType.INT);
		assertEquals("COALESCE( MAX( COLA ), 0 ) AS COLA", func.apply(column, dialect));
	}

	@Test
	void testDouble() {
		Column column = new Column("COLA");
		column.setDataType(DataType.DOUBLE);
		assertEquals("MAX( NULL ) AS COLA", func.apply(column, dialect));
	}

	@Test
	void testPK() {
		Table table = new Table("taba");
		table.getColumns().add(c -> {
			c.setName("COLA").setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("COLB").setDataType(DataType.VARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("COLC").setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("COLD").setDataType(DataType.VARCHAR);
		});
		table.setPrimaryKey("PK", table.getColumns().get("COLA"), table.getColumns().get("COLB"));
		assertEquals("COALESCE( MAX( COLA ), 0 ) AS COLA", func.apply(table.getColumns().get("COLA"), dialect));
		assertEquals("MAX( COLB ) AS COLB", func.apply(table.getColumns().get("COLB"), dialect));
		assertNull(func.apply(table.getColumns().get("COLC"), dialect));
		assertNull(func.apply(table.getColumns().get("COLD"), dialect));
	}

}
