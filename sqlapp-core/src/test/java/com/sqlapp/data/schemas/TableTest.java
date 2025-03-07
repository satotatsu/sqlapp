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

import java.text.ParseException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.util.DateUtils;

public class TableTest extends AbstractDbObjectTest<Table> {

	public static Table getTable(final String tableName) {
		final Table table = new Table(tableName);
		table.setCharacterSemantics(CharacterSemantics.Char);
		table.setCharacterSet("UTF8");
		table.setCollation("utf8_bin");
		table.setCompression(true);
		table.setCompressionType("ROW");
		final Column column = getColumn("A", DataType.VARCHAR);
		column.setLength(1).setNullable(false).setRemarks("カラムA");
		column.setCheck("A>2");
		column.getExtendedProperties().put("INITIAL", "TRUE");
		column.getSpecifics().put("TABLE_SPACE", "TABLE_SPACEA");
		column.getValues().add("1");
		column.getValues().add("2");
		column.getValues().add("3");
		column.setRemarks("コメント");
		column.addDefinition("DDL1行目");
		column.addDefinition("DDL2行目");
		//
		table.getColumns().add(column);
		//
		final Column column1 = getColumn("B", DataType.BIGINT);
		table.getColumns().add(column1);
		//
		final Column column2 = getColumn("C", DataType.DATETIME);
		table.getColumns().add(column2);
		//
		final Column column3 = getColumn("D", DataType.BOOLEAN);
		table.getColumns().add(column3);
		//
		final Column column4 = getColumn("E", DataType.TINYINT);
		column4.setIdentity(true).setIdentityLastValue(1)
				.setIdentityStartValue(0).setIdentityStep(2);
		table.getColumns().add(column4);
		//
		final Column column5 = getColumn("F", DataType.TINYINT);
		table.getColumns().add(column5);
		//
		final Column column6 = getColumn("G", DataType.TINYINT).setSequenceName("seq1");
		table.getColumns().add(column6);
		//
		table.setPrimaryKey("PK_TABLEA", column, column1);
		//
		final Index index = new Index("IDX_TABLEA_1");
		index.setUnique(true);
		index.getColumns().add(new Column("E"));
		table.getIndexes().add(index);
		//
		Row row = table.newRow();
		row.put(column, "VALA1");
		row.putRemarks(column, column.getName()+"Comment");
		row.putOption(column, column.getName()+"Option");
		row.put(column1, 1);
		row.putRemarks(column1, column1.getName()+"Comment");
		row.putOption(column1, column1.getName()+"Option");
		try {
			row.put(column2, DateUtils.parse("2017-02-22 10:23.34"));
		} catch (final ParseException e) {
		}
		row.putRemarks(column2, column2.getName()+"Comment");
		row.putOption(column2, column2.getName()+"Option");
		table.getRows().add(row);
		//
		row = table.newRow();
		row.put(column, "VALA2$'\"><");
		row.put(column1, 2);
		try {
			row.put(column2, DateUtils.parse("2017-02-22 10:23.35"));
		} catch (final ParseException e) {
		}
		table.getRows().add(row);
		// ユニーク制約
		final UniqueConstraint unique2 = new UniqueConstraint("UNIQUE2", column5);
		table.getConstraints().add(unique2);
		// チェック制約
		final CheckConstraint check1 = new CheckConstraint("CHECK1", "A>0 AND B>0",
				column, column1);
		table.getConstraints().add(check1);
		// パーティショニング
		final Partitioning partitioning = new Partitioning();
		table.setPartitioning(partitioning);
		partitioning.getPartitioningColumns().add("B");
		partitioning.getSubPartitioningColumns().add("E");
		final Partition partition=new Partition();
		partition.setHighValue("2017-05-01");
		partition.setCompression(true);
		final SubPartition subPartition=createSubPartition("sub1", "10");
		partition.getSubPartitions().add(subPartition);
		partitioning.getPartitions().add(partition);
		// テーブルスペース
		table.setTableSpaceName("tableSpaceA");
		return table;
	}

	public static Column getColumn(final String name, final DataType types) {
		final Column column = new Column(name);
		column.setDataType(types);
		return column;
	}
	
	protected static SubPartition createSubPartition(final String name, final String highValue){
		final SubPartition subPartition=new SubPartition(name);
		subPartition.setHighValue(highValue);
		subPartition.setCompression(true);
		return subPartition;
	}

	@Override
	protected Table getObject() {
		final Table table = getTable("TableA");
		table.getColumns().get("B").setName("B1").setDataType(DataType.INT);
		table.getInherits().add(getTable("TableAParent"));
		final Row row = table.newRow();
		row.put("B1", 1);
		table.getRows().add(row);
		return table;
	}

	@Override
	protected TableXmlReaderHandler getHandler() {
		return new TableXmlReaderHandler();
	}

	@Override
	protected void testDiffString(final Table obj1, final Table obj2) {
		obj2.getColumns().get(0).setSequenceName("seqA")
				.setDataType(DataType.NVARCHAR);
		final DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}

	@Test
	public void testColumn() {
		final Table table = getObject();
		table.setDialect(DialectResolver.getInstance()
				.getDialect("mysql", 5, 1));
		final Column column = new Column("testc");
		column.setDataTypeName("char");
		column.setLength(10);
		column.setOctetLength(30);
		table.getColumns().add(column);
		assertEquals(10L, column.getLength().longValue());
		assertEquals(30L, column.getOctetLength().longValue());
		assertEquals(DataType.CHAR, column.getDataType());
		//
		column.setDataTypeName("char");
		table.getColumns().add(column);
		assertEquals(10L, column.getLength().longValue());
		assertEquals(30L, column.getOctetLength().longValue());
	}
}
