/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractPartition;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public class SapHanaTruncatePartitionFactoryTest extends AbstractSapHanaSqlFactoryTest {
	SqlFactory<AbstractPartition<?>> operationfactory;

	@BeforeEach
	public void before() {
		operationfactory = sqlFactoryRegistry.getSqlFactory(new Partition(), SqlType.TRUNCATE);
	}

	@Test
	public void testGetDdlTable() {
		final Table table = createTable();
		Partition partition=table.getPartitioning().getPartitions().get(0); 
		List<SqlOperation> list = operationfactory.createSql(partition);
		SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("truncate_partition1.sql");
		assertEquals(expected, commandText.getSqlText());
	}

	@Test
	public void testGetDdlTable2() {
		final Table table = createTable();
		Partition partition=table.getPartitioning().getPartitions().get(1); 
		List<SqlOperation> list = operationfactory.createSql(partition);
		assertEquals(2, list.size());
		SqlOperation commandText = list.get(0);
		System.out.println(list);
		String expected = getResource("truncate_partition2_1.sql");
		assertEquals(expected, commandText.getSqlText());
		commandText = list.get(1);
		System.out.println(list);
		expected = getResource("truncate_partition2_2.sql");
		assertEquals(expected, commandText.getSqlText());
	}

	protected Table createTable(){
		final Table table = new Table("tableB");
		table.getColumns().add(
				new Column("colA").setDataType(DataType.INT).setNotNull(true));
		table.getColumns()
				.add(new Column("colB").setDataType(DataType.BIGINT).setCheck(
						"colB>0"));
		table.getColumns().add(
				new Column("colC").setDataType(DataType.VARCHAR).setLength(10)
						.setDefaultValue("'0'").setNotNull(true));
		table.setPrimaryKey("PK_TABLEA", table.getColumns().get("colA"), table
				.getColumns().get("colB"));
		table.toPartitioning();
		table.getPartitioning().getPartitions().add(p->{
			p.setId("123");
		});
		table.getPartitioning().getPartitions().add(p->{
			p.setId("124");
			p.getSubPartitions().add(s->{
				s.setId("1241");
			});
			p.getSubPartitions().add(s->{
				s.setId("1242");
			});
			assertEquals(2, p.getSubPartitions().size());
		});
		return table;
	}

}
