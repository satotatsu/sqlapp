/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.IndexType;
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
public class OracleAlterTableFactoryTest extends AbstractOracleSqlFactoryTest {
	SqlFactory<Table> operation;

	@BeforeEach
	public void before() {
		operation = this.sqlFactoryRegistry.getSqlFactory(
				new Table(), State.Modified);
	}


	@Test
	public void testStorage() {
		Table table1 = getTable1("tableA");
		table1.getSpecifics().put("PCT_FREE", 11);
		table1.getSpecifics().put("INI_TRANS", 3);
		table1.getSpecifics().put("MAX_TRANS", 254);
		table1.getSpecifics().put("INITIAL_EXTENT", "1023M");
		table1.getSpecifics().put("NEXT_EXTENT", "1023M");
		table1.getSpecifics().put("MIN_EXTENTS", 2);
		table1.getSpecifics().put("MAX_EXTENTS", 2147483644);
		table1.getSpecifics().put("FREELISTS", 2);
		table1.getSpecifics().put("FREELIST_GROUPS", 2);
		table1.getSpecifics().put("BUFFER_POOL", "DEFAULT");
		Table table2 = getTable1("tableA");
		table2.getSpecifics().put("PCT_FREE", 10);
		table2.getSpecifics().put("INI_TRANS", 2);
		table2.getSpecifics().put("MAX_TRANS", 255);
		table2.getSpecifics().put("INITIAL_EXTENT", "1024M");
		table1.getSpecifics().put("NEXT_EXTENT", "1024M");
		table2.getSpecifics().put("NEXT_EXTENT", 1);
		table2.getSpecifics().put("MAX_EXTENTS", 2147483645);
		table2.getSpecifics().put("FREELISTS", 1);
		table2.getSpecifics().put("FREELIST_GROUPS", 1);
		table2.getSpecifics().put("BUFFER_POOL", "DEFAULT");
		DbObjectDifference diff=table1.diff(table2);
		List<SqlOperation> list = operation.createDiffSql(diff);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table0.sql");
		assertEquals(expected, operation.getSqlText());
	}

	
	@Test
	public void testForeinfKey() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		Table table3 = getTable1("tableB");
		table3.getColumns().get("cola").setName("cola1");
		table3.getColumns().get("colb").setName("colb1");
		table2.getConstraints().addForeignKeyConstraint(
				"tableA_tableb_fk",
				new Column[] { table2.getColumns().get("colb"),
						table2.getColumns().get("cola") },
				new Column[] { table3.getColumns().get("cola1"),
						table3.getColumns().get("colb1") });
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table2.sql");
		assertEquals(expected, operation.getSqlText());
	}

	private Table getTable(String tableName) {
		Table table = new Table(tableName);
		return table;
	}

	private Table getTable1(String tableName) {
		Table table = getTable(tableName);
		Column column = new Column("cola").setDataType(DataType.INT);
		table.getColumns().add(column);
		column = new Column("colb").setDataType(DataType.VARCHAR).setLength(50)
				.setCharacterSet("utf8").setCollation("utf8mb4_binary");
		table.getColumns().add(column);
		column = new Column("colc").setDataType(DataType.DATETIME);
		table.getColumns().add(column);
		return table;
	}

	/**
	 * Primary Key制約テスト1
	 */
	@Test
	public void testGetDdlTableTablePrimaryKey1() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table1.getConstraints().addPrimaryKeyConstraint("pk1",
				table1.getColumns().get("cola"));
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		SqlOperation commandText = CommonUtils.first(list);
		assertEquals("ALTER TABLE \"tableA\" DROP CONSTRAINT \"pk1\"",
				commandText.getSqlText());
		System.out.println(list);
	}

	/**
	 * Primary Key制約テスト2
	 */
	@Test
	public void testGetDdlTableTablePrimaryKey2() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table2.getConstraints().addPrimaryKeyConstraint("pk1",
				table2.getColumns().get("cola"));
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		int i=0;
		SqlOperation commandText = list.get(i++);
		assertEquals(
				"ALTER TABLE \"tableA\" MODIFY (\n	  \"cola\" NUMBER(9,0) NOT NULL\n)",
				commandText.getSqlText());
		commandText = list.get(i++);
		assertEquals(
				"ALTER TABLE \"tableA\" ADD CONSTRAINT \"pk1\" PRIMARY KEY ( \"cola\" )",
				commandText.getSqlText());
		System.out.println(list);
	}

	/**
	 * Primary Key制約テスト2
	 */
	@Test
	public void testGetDdlTableTablePrimaryKey3() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table1.getConstraints().addPrimaryKeyConstraint("pk1",
				table2.getColumns().get("cola"));
		table2.getConstraints()
				.addPrimaryKeyConstraint("pk1", table2.getColumns().get("cola"))
				.setPrimaryKey(false);
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		int i=0;
		SqlOperation commandText = list.get(i++);
		assertEquals(
				"ALTER TABLE \"tableA\" MODIFY (\n	  \"cola\" NUMBER(9,0) NOT NULL\n)",
				commandText.getSqlText());
		commandText = list.get(i++);
		assertEquals(
				"ALTER TABLE \"tableA\" DROP CONSTRAINT \"pk1\"",
				commandText.getSqlText());
		System.out.println(list);
	}

	/**
	 * Indexテスト1
	 */
	@Test
	public void testGetDdlTableTableIndex1() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table2.getColumns().add(new Column("d").setDataType(DataType.INT));
		table1.getIndexes().add("index1", table1.getColumns().get("cola"));
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		int i=0;
		SqlOperation operation = list.get(i++);
		String expected;
		expected = getResource("alter_table_drop_index1.sql");
		assertEquals(
				expected,
				operation.getSqlText());
		operation = list.get(i++);
		expected = getResource("alter_table3.sql");
		assertEquals(expected,
				operation.getSqlText());
		System.out.println(list);
	}

	/**
	 * Indexテスト2
	 */
	@Test
	public void testGetDdlTableTableIndex2() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table2.getIndexes().add("index1", table1.getColumns().get("cola"));
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		int i=0;
		SqlOperation operation = list.get(i++);
		String expected = getResource("alter_table4.sql");
		assertEquals(
				expected,
				operation.getSqlText());
		System.out.println(list);
	}

	/**
	 * Indexテスト3
	 */
	@Test
	public void testGetDdlTableTableIndex3() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table2.getIndexes().add("index1", table1.getColumns().get("cola"))
				.setIndexType(IndexType.FullText);
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		int i=0;
		SqlOperation operation = list.get(i++);
		String expected = getResource("alter_table_add_index2.sql");
		assertEquals(
				expected,
				operation.getSqlText());
		System.out.println(list);
	}

	/**
	 * Partitionテスト1
	 */
	@Test
	public void testGetDdlTableTablePartition1() {
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
	public void testGetDdlTableTablePartition2() {
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
	 * Partitionテスト(サブパーティション追加)
	 */
	@Test
	public void testGetDdlTableTablePartition3() {
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
	public void testGetDdlTableTablePartition4() {
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
	public void testGetDdlTableTablePartition5() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		Partitioning partitionInfo1 = getPartitionInfo1(table1);
		table1.setPartitioning(partitionInfo1);
		Partitioning partitionInfo2 = getPartitionInfo1(table2);
		table2.setPartitioning(partitionInfo2);
		partitionInfo2.getPartitions().remove(0);
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		SqlOperation commandText = CommonUtils.first(list);
		String[] expected = new String[] { "ALTER TABLE \"tableA\" DROP PARTITION \"p0\"" };
		String[] result = commandText.getSqlText().split("\n");
		System.out.println(list);
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i], result[i]);
		}
	}

	private Partitioning getPartitionInfo1(Table table) {
		Partitioning partitionInfo = new Partitioning();
		partitionInfo.setPartitioningType(PartitioningType.Range);
		partitionInfo.getPartitioningColumns().add(
				table.getColumns().get("cola"));
		List<Partition> partitions = getPartitions("p", 0, 3);
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
