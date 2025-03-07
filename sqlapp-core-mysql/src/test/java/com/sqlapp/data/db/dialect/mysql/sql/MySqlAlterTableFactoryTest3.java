/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.SubPartition;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * MySQL用のAlterコマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class MySqlAlterTableFactoryTest3 extends AbstractMySqlSqlFactoryTest {
	SqlFactory<Table> operation;

	@BeforeEach
	public void before() {
		operation = this.sqlFactoryRegistry.getSqlFactory(
				new Table(), State.Modified);
	}

	
	private Table getTable(String tableName) {
		Table table = new Table(tableName);
		table.getSpecifics().put("ENGINE", "innodb");
		return table;
	}

	private Table getTable1(String tableName) {
		Table table = getTable(tableName);
		Column column = new Column("cola").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(column);
		column = new Column("colb").setDataType(DataType.VARCHAR).setLength(50)
				.setCharacterSet("utf8").setCollation("utf8mb4_binary");
		table.getColumns().add(column);
		column = new Column("colc").setDataType(DataType.DATETIME);
		table.getColumns().add(column);
		return table;
	}

	/**
	 * Partitionテスト1
	 */
	@Test
	public void testTablePartition1() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		Partitioning partitionInfo = new Partitioning();
		partitionInfo.setPartitioningType(PartitioningType.Hash);
		partitionInfo.getPartitioningColumns().add(
				table1.getColumns().get("cola"));
		partitionInfo.setPartitionSize(10);
		table2.setPartitioning(partitionInfo);
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		SqlOperation operation = CommonUtils.first(list);
		String expected = getResource("alter_table_partition1.sql");
		System.out.println(operation.getSqlText());
		assertEquals(expected, operation.getSqlText());
	}

	/**
	 * Partitionテスト2
	 */
	@Test
	public void testTablePartition2() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		Partitioning partitionInfo = getPartitionInfo1(table2);
		table2.setPartitioning(partitionInfo);
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		SqlOperation operation = CommonUtils.first(list);
		String expected = getResource("alter_table_partition2.sql");
		System.out.println(operation.getSqlText());
		assertEquals(expected, operation.getSqlText());
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


	/**
	 * merge Partitionテスト1
	 */
	@Test
	public void testTableDropPartition1() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		Partitioning partitionInfo1 = getPartitionInfo1(table1, 5);
		table1.setPartitioning(partitionInfo1);
		Partitioning partitionInfo2 = getPartitionInfo1(table2, 3);
		table2.setPartitioning(partitionInfo2);
		partitionInfo2.getPartitions().get(2).setName("p4").setTableSpaceName("table_space4").setRemarks("p4 partition");
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		SqlOperation operation = CommonUtils.first(list);
		String expected = getResource("alter_table_drop_partition1.sql");
		System.out.println(operation.getSqlText());
		assertEquals(expected, operation.getSqlText());
	}

	/**
	 * merge Partitionテスト1
	 */
	@Test
	public void testTableMergePartition1() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		Partitioning partitionInfo1 = getPartitionInfo1(table1, 5);
		table1.setPartitioning(partitionInfo1);
		Partitioning partitionInfo2 = getPartitionInfo1(table2, 3);
		table2.setPartitioning(partitionInfo2);
		partitionInfo2.getPartitions().get(2).setName("p4").setTableSpaceName("table_space4").setRemarks("p4 partition");
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		SqlOperation operation = CommonUtils.first(list);
		String expected = getResource("alter_table_merge_partition1.sql");
		//assertEquals(expected, operation.getSqlText());
		//TODO
	}

	
	/**
	 * Partitionテスト(サブパーティション追加)
	 */
	@Test
	public void testTablePartition3() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		Partitioning partitionInfo = getPartitionInfo2(table2);
		table2.setPartitioning(partitionInfo);
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		SqlOperation operation = CommonUtils.first(list);
		String expected = getResource("alter_table_partition3.sql");
		System.out.println(operation.getSqlText());
		assertEquals(expected, operation.getSqlText());
	}

	/**
	 * Partitionテスト(パーティション追加)
	 */
	@Test
	public void testTablePartition4() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		Partitioning partitionInfo1 = getPartitionInfo1(table1);
		table1.setPartitioning(partitionInfo1);
		partitionInfo1.getPartitions().remove(0);
		partitionInfo1.getPartitions().remove(0);
		Partitioning partitionInfo2 = getPartitionInfo1(table2);
		table2.setPartitioning(partitionInfo2);
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		SqlOperation operation = CommonUtils.first(list);
		String expected = getResource("alter_table_partition4.sql");
		System.out.println(operation.getSqlText());
		assertEquals(expected, operation.getSqlText());
	}

	/**
	 * Partitionテスト(パーティション削除)
	 */
	@Test
	public void testTablePartition5() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		Partitioning partitionInfo1 = getPartitionInfo1(table1);
		table1.setPartitioning(partitionInfo1);
		Partitioning partitionInfo2 = getPartitionInfo1(table2);
		table2.setPartitioning(partitionInfo2);
		partitionInfo2.getPartitions().remove(0);
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		SqlOperation commandText = CommonUtils.first(list);
		String[] expected = new String[] { "ALTER TABLE `tableA` DROP PARTITION p0" };
		String[] result = commandText.getSqlText().split("\n");
		System.out.println(list);
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i], result[i]);
		}
	}
	
	/**
	 * Partitioning無効化テスト
	 */
	@Test
	public void testTablePartition6() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		Partitioning partitionInfo = getPartitionInfo1(table2);
		table2.setPartitioning(partitionInfo);
		SqlFactoryRegistry sqlFactoryRegistry= createSqlFactoryRegistry();
		sqlFactoryRegistry.deregisterSqlFactory(Partitioning.class, SqlType.CREATE);
		SqlFactory<Table> sqlFactory= sqlFactoryRegistry.getSqlFactory(
				new Table(), State.Modified);
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		assertEquals(0, list.size());
	}

	private Partitioning getPartitionInfo1(Table table) {
		return getPartitionInfo1(table, 3);
	}

	private Partitioning getPartitionInfo1(Table table, int size) {
		Partitioning partitionInfo = new Partitioning();
		partitionInfo.setPartitioningType(PartitioningType.Range);
		partitionInfo.getPartitioningColumns().add(
				table.getColumns().get("cola"));
		List<Partition> partitions = getPartitions("p", 0, size);
		partitionInfo.getPartitions().addAll(partitions);
		return partitionInfo;
	}

	private Partitioning getPartitionInfo2(Table table) {
		Partitioning partitionInfo = new Partitioning();
		partitionInfo.setPartitioningType(PartitioningType.Range);
		partitionInfo.setSubPartitioningType(PartitioningType.Range);
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

}
