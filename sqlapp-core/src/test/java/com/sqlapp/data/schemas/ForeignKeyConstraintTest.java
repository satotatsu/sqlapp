/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.schemas;

import com.sqlapp.data.db.datatype.DataType;
import static org.junit.jupiter.api.Assertions.*;
public class ForeignKeyConstraintTest extends AbstractDbObjectTest<ForeignKeyConstraint> {

	@Override
	protected ForeignKeyConstraint getObject() {
		ForeignKeyConstraint fk = new ForeignKeyConstraint();
		fk.setName("FKNAME");
		fk.setRemarks("コメント");
		Table table = new Table("table1");
		Column column = new Column("A");
		column.setDataType(DataType.VARCHAR);
		table.getColumns().add(column);
		fk.addColumns(column);
		column = new Column("B");
		table.getColumns().add(column);
		fk.addColumns(column);
		//
		Table table2 = new Table("table2");
		table2.setSchemaName("schema2");
		column = new Column("RA");
		table2.getColumns().add(column);
		fk.addRelatedColumn(column);
		column = new Column("RB");
		table2.getColumns().add(column);
		fk.addRelatedColumn(column);
		//
		table.getConstraints().add(fk);
		assertEquals(1, table2.getChildRelations().size());
		//
		assertEquals(table.getName(), fk.getTableName());
		assertEquals(table.getSchemaName(), fk.getSchemaName());
		assertEquals(table.getName(), fk.getTable().getName());
		assertEquals(table.getSchemaName(), fk.getTable().getSchemaName());
		//
		assertEquals(table2.getName(), fk.getRelatedTableName());
		assertEquals(table2.getSchemaName(), fk.getRelatedTableSchemaName());
		assertEquals(table2.getName(), fk.getRelatedTable().getName());
		assertEquals(table2.getSchemaName(), fk.getRelatedTable().getSchemaName());
		//
		return fk;
	}

	@Override
	protected ForeignKeyConstraintXmlReaderHandler getHandler() {
		return new ForeignKeyConstraintXmlReaderHandler();
	}

	@Override
	protected void testDiffString(ForeignKeyConstraint obj1,
			ForeignKeyConstraint obj2) {
		obj2.setName("b");
		Column column = new Column("C");
		obj2.addColumns(column);
		obj2.setRemarks("コメントB");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}

}
