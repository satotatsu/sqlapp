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
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * MySQL用のAlterコマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class MySqlAlterTableFactoryTest extends AbstractMySqlSqlFactoryTest {
	SqlFactory<Table> sqlFactory;

	@BeforeEach
	public void before() {
		sqlFactory = this.sqlFactoryRegistry.getSqlFactory(
				new Table(), State.Modified);
	}

	@Test
	public void testGetDdlTableTable1() {
		Table table1 = getTable("tableA");
		Table table2 = getTable("tableA");
		table1.setCharacterSet("UTF8");
		table2.getSpecifics().put("ENGINE", "myisam");
		table2.setCollation("UTF8MB4_GENERAL_CI");
		DbObjectDifference diff=table1.diff(table2);
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table1.sql");
		assertEquals(expected, operation.getSqlText());
		//
		list = sqlFactory.createDiffSql(diff.reverse());
		operation = CommonUtils.first(list);
		System.out.println(list);
		expected = getResource("alter_table1_reverse.sql");
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testForeinfKey() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableB");
		Table table3 = getTable1("tableA");
		table3.getColumns().get("cola").setName("cola1");
		table3.getColumns().get("colb").setName("colb1");
		ForeignKeyConstraint fk=table2.getConstraints().addForeignKeyConstraint(
				"tableA_tableb_fk",
				new Column[] { table2.getColumns().get("colb"),
						table2.getColumns().get("cola") },
				new Column[] { table3.getColumns().get("cola1"),
						table3.getColumns().get("colb1") });
		fk.setRemarks("remark1");
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table2.sql");
		assertEquals(expected, operation.getSqlText());
	}
	
	@Test
	public void testForeinfKey2() {
		Table table1 = getTable1("tableB");
		Table table2 = getTable1("tableB");
		Table table3 = getTable1("tableA");
		table3.getColumns().get("cola").setName("cola1");
		table3.getColumns().get("colb").setName("colb1");
		table2.getConstraints().addForeignKeyConstraint(
				"tableA_tableb_fk",
				new Column[] { table2.getColumns().get("colb")},
				new Column[] { table3.getColumns().get("cola1")});
		table1.getColumns().remove("colb");
		List<SqlOperation> list = sqlFactory.createDiffSql(table2.diff(table1));
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table3.sql");
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testForeinfKey3() {
		Table table1 = getTable1("tableB");
		Table table2 = getTable1("tableB");
		Table table3 = getTable1("tableA");
		table3.getColumns().get("cola").setName("cola1");
		table3.getColumns().get("colb").setName("colb1");
		table2.getConstraints().addForeignKeyConstraint(
				"tableA_tableb_fk",
				new Column[] { table2.getColumns().get("colb")},
				new Column[] { table3.getColumns().get("cola1")});
		List<SqlOperation> list = sqlFactory.createDiffSql(table2.diff(table1));
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table5.sql");
		assertEquals(expected, operation.getSqlText());
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

	@Test
	public void testGetDdlTableTable2() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table2.setName("tableA1");
		table2.getSpecifics().put("ENGINE", "myisam");
		table2.setCharacterSet("utf8")
				.setCollation("utf8mb4_binary");
		table2.getColumns().get("colb").setLength(60);
		table2.getColumns().remove("cola");
		//
		Column column = new Column("cold").setDataType(DataType.UINT)
				.setRemarks("cold remark!").setDefaultValue("12");
		table2.getColumns().add(column);
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table4.sql");
		assertEquals(expected, operation.getSqlText());
	}

	/**
	 * AUTO_INCREMENTテスト
	 */
	@Test
	public void testGetDdlTableTableAutoIncrement() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table2.getColumns().get("cola").setIdentity(true)
				.setIdentityLastValue(10);
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		assertEquals(
				"ALTER TABLE `tableA` MODIFY cola INT AUTO_INCREMENT FIRST",
				commandText.getSqlText());
		assertEquals("ALTER TABLE `tableA` AUTO_INCREMENT =10", list.get(1)
				.getSqlText());
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
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		SqlOperation commandText = CommonUtils.first(list);
		assertEquals("ALTER TABLE `tableA` DROP PRIMARY KEY",
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
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		SqlOperation commandText = CommonUtils.first(list);
		assertEquals(
				"ALTER TABLE `tableA` ADD CONSTRAINT pk1 PRIMARY KEY ( cola )",
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
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		SqlOperation commandText = CommonUtils.first(list);
		assertEquals(
				"ALTER TABLE `tableA` DROP PRIMARY KEY, ADD CONSTRAINT pk1 UNIQUE ( cola )",
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
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		SqlOperation commandText = CommonUtils.first(list);
		assertEquals(
				"ALTER TABLE `tableA` ADD d INT AFTER colc, DROP INDEX index1",
				commandText.getSqlText());
		System.out.println(list);
	}

	/**
	 * Indexテスト2
	 */
	@Test
	public void testGetDdlTableTableIndex2() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table2.getColumns().add(new Column("d").setDataType(DataType.INT));
		table2.getIndexes().add("index1", table1.getColumns().get("cola"));
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		SqlOperation commandText = CommonUtils.first(list);
		assertEquals(
				"ALTER TABLE `tableA` ADD d INT AFTER colc, ADD INDEX index1 ( cola )",
				commandText.getSqlText());
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
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		SqlOperation commandText = CommonUtils.first(list);
		assertEquals("ALTER TABLE `tableA` ADD FULLTEXT INDEX index1 ( cola )",
				commandText.getSqlText());
		System.out.println(list);
	}

	/**
	 * Indexテスト4
	 */
	@Test
	public void testGetDdlTableTableIndex4() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table2.getIndexes().add("index1", table1.getColumns().get("cola"))
				.setIndexType(IndexType.Spatial);
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		SqlOperation commandText = CommonUtils.first(list);
		assertEquals("ALTER TABLE `tableA` ADD SPATIAL INDEX index1 ( cola )",
				commandText.getSqlText());
		System.out.println(list);
	}

}
