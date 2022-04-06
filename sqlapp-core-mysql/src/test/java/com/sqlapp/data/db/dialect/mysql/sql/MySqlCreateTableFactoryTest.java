/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.SubPartition;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * MySQL用のCreateコマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class MySqlCreateTableFactoryTest extends AbstractMySqlSqlFactoryTest {
	SqlFactory<Table> operation;

	@BeforeEach
	public void before() {
		operation = this.sqlFactoryRegistry.getSqlFactory(new Table(),
				SqlType.CREATE);
	}

	@Test
	public void testGetDdlTableTable1() {
		Table table1 = getTable1("tableA");
		List<SqlOperation> list = operation.createSql(table1);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_table1.sql");
		assertEquals(expected, operation.getSqlText());
	}

	private Table getTable(String tableName) {
		Table table = new Table(tableName);
		table.getSpecifics().put("ENGINE", "innodb");
		return table;
	}

	private Table getTable1(String tableName) {
		Table table = getTable(tableName);
		table.setDialect(dialect);
		table.setRemarks("comment!!!");
		table.getColumns().add("cola", c->{
			c.setDataType(DataType.INT);
		});
		table.getColumns().add("colb", c->{
			c.setDataType(DataType.BIGINT);
		});
		table.getColumns().add("colc", c->{
			c.setDataType(DataType.VARCHAR).setLength(50)
			.setCharacterSet("utf8").setCollation("utf8mb4_binary");
		});
		table.getColumns().add("cold", c->{
			c.setDataType(DataType.DATETIME);
			c.setLength(1);
		});
		table.getColumns().add("cole", c->{
			c.setDataType(DataType.ENUM).setDataTypeName(
					"enum('a', 'b', 'c')");
		});
		//
		table.getIndexes().add(idx->{
			idx.setName("indexa").setIndexType(IndexType.Hash).getColumns().add("colc").setLength(10);
		});
		Partitioning partitionInfo = getPartitionInfo(table);
		table.setPartitioning(partitionInfo);
		return table;
	}

	private Partitioning getPartitionInfo(Table table) {
		Partitioning partitionInfo = new Partitioning();
		partitionInfo.setPartitioningType(PartitioningType.Range);
		partitionInfo.setSubPartitioningType(PartitioningType.Key);
		partitionInfo.getPartitioningColumns().add(
				table.getColumns().get("cola"));
		partitionInfo.getSubPartitioningColumns().add(
				table.getColumns().get("colb"));
		List<Partition> partitions = getPartitions("p", 0, 1);
		partitionInfo.getPartitions().addAll(partitions);
		List<SubPartition> subpartitions = getSubPartitions("s", 0, 3);
		CommonUtils.first(partitions).getSubPartitions().addAll(subpartitions);
		return partitionInfo;
	}

	private List<Partition> getPartitions(String baseName, int start, int size) {
		List<Partition> partitions = CommonUtils.list();
		for (int i = start; i < (start + size); i++) {
			Partition partition = new Partition(baseName + i).setRemarks(
					baseName + i + " partition").setTableSpaceName(
					"table_space" + i);
			if (i == ((start + size) - 1)) {
				partition.setHighValue("MAXVALUE");
			} else {
				partition.setHighValue(i);
			}
			partitions.add(partition);
		}
		return partitions;
	}
	
	private List<SubPartition> getSubPartitions(String baseName, int start, int size) {
		List<SubPartition> partitions = CommonUtils.list();
		for (int i = start; i < (start + size); i++) {
			SubPartition partition = new SubPartition(baseName + i).setRemarks(
					baseName + i + " partition").setTableSpaceName(
					"table_space" + i);
			if (i == ((start + size) - 1)) {
				partition.setHighValue("MAXVALUE");
			} else {
				partition.setHighValue(i);
			}
			partitions.add(partition);
		}
		return partitions;
	}
}
