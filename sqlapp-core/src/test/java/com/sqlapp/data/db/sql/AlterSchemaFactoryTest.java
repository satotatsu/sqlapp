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

package com.sqlapp.data.db.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;

public class AlterSchemaFactoryTest extends AbstractStandardFactoryTest {

	@BeforeEach
	public void before() {
	}

	@Test
	public void testGetDdlTable() {
		final Schema schema1 = getSchema1("schemaA");
		final Schema schema2 = getSchema2("schemaA");
		final SqlFactory<Schema> command = dialect
				.createSqlFactoryRegistry().getSqlFactory(
						new Schema(), State.Modified);
		final DbObjectDifference diff = schema1.diff(schema2);
		final List<SqlOperation> list = command.createDiffSql(diff);
		System.out.println(list);
		final String expected = getResource("alter_schema1.sql");
		assertEquals(expected, list.toString());

	}

	protected Schema getSchema(final String name) {
		final Schema schema = new Schema();
		return schema;
	}

	protected Schema getSchema1(final String name) {
		final Schema schema = getSchema(name);
		schema.getTables().add(getTable("tableA"));
		schema.getTables().add(getTable("tableB"));
		return schema;
	}

	protected Schema getSchema2(final String name) {
		final Schema schema = getSchema(name);
		schema.getTables().add(getTable("table1"));
		schema.getTables().add(getTable("tableA"));
		schema.getTables().add(getTable("tableB"));
		schema.getTables().add(getTable("tableZ"));
		final Table table = schema.getTables().get("tableA");
		table.getColumns().remove(2);
		table.getColumns().get("colB").setCheck(null);
		return schema;
	}

	protected Table getTable(final String name) {
		final Table table = new Table(name);
		table.getColumns().add(
				new Column("colA").setDataType(DataType.INT).setNotNull(true));
		table.getColumns()
				.add(new Column("colB").setDataType(DataType.BIGINT).setCheck(
						"colB>0"));
		table.getColumns().add(
				new Column("colC").setDataType(DataType.VARCHAR).setLength(10)
						.setDefaultValue("''"));
		table.setPrimaryKey("PK_TABLEA", table.getColumns().get("colA"), table
				.getColumns().get("colB"));
		table.getConstraints().addUniqueConstraint("UK_tableA1",
				table.getColumns().get("colB"));
		table.getIndexes().add("IDX_tableA1", table.getColumns().get("colC"))
				.getColumns().get(0).setOrder(Order.Desc);
		return table;
	}

}
