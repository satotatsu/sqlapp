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

package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.UnsupportedEncodingException;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractTest;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

public class TableTest2 extends AbstractTest {

	@Test
	public void testDiff() throws XMLStreamException, UnsupportedEncodingException {
		final Table obj1 = getTable("table1");
		final Table obj2 = getTable("table2");
		setTable(obj1, "pre1");
		setTable(obj2, "pre2");
		//
		final DbObjectDifference diff1 = obj1.diff(obj2);
		final DbObjectDifference diff2 = obj2.diff(obj1);
		this.testDiffString("table2-1", diff1);
		this.testDiffString("table2-2", diff2);
	}

	protected void testDiffString(final DbObjectDifference diff) {
		testDiffString(CommonUtils.initCap(this.getClass().getSimpleName().replace("Test", "")), diff);
	}

	protected void testDiffString(final String resourceName, final DbObjectDifference diff) {
		assertEquals(FileUtils.getResource(this, resourceName + ".diff"), diff.toString());
	}

	private Table getTable(final String name) {
		final Table table = new Table(name);
		Column col = table.newColumn();
		col.setName("col1").setDataType(DataType.INT);
		table.getColumns().add(col);
		//
		col = table.newColumn();
		col.setName("col2").setDataType(DataType.VARCHAR);
		table.getColumns().add(col);
		table.getConstraints().addPrimaryKeyConstraint("PK", table.getColumns().get("col1"));
		return table;
	}

	private void setTable(final Table table, final String prefix) {
		for (int i = 0; i < 5; i++) {
			final Row row = table.newRow();
			row.put(0, i);
			if (i % 3 == 0) {
				row.put(1, "dummy" + i);
			} else {
				row.put(1, prefix + "dummy" + i);
			}
			table.getRows().add(row);
		}
	}

}
