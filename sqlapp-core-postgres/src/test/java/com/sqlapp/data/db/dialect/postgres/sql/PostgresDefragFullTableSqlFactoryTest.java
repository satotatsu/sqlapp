/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.ExcludeConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.NullsOrder;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * MySQL用のCreateコマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class PostgresDefragFullTableSqlFactoryTest extends AbstractPostgresSqlFactoryTest {
	SqlFactory<Table> operation;

	@BeforeEach
	public void before() {
		sqlFactoryRegistry.getOption().setQuateObjectName(false);
		sqlFactoryRegistry.getOption().setQuateColumnName(false);
		sqlFactoryRegistry.getOption().getTableOptions().setOnlineIndex(true);
		operation = this.sqlFactoryRegistry.getSqlFactory(new Table(),
				SqlType.DEFRAG_FULL);
	}

	@Test
	public void testGetDdlTableTable1() {
		Table table1 = getTable1("tableA");
		List<SqlOperation> list = operation.createSql(table1);
		assertEquals(1, list.size());
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("defrag_full_table1.sql");
		assertEquals(expected, operation.getSqlText());
	}

	private Table getTable(String tableName) {
		Table table = new Table(tableName);
		return table;
	}

	private Table getTable1(String tableName) {
		Table table = getTable(tableName);
		table.setRemarks("tableA comment");
		table.setDialect(dialect);
		table.setUnlogged(true);
		table.setRemarks("comment!!!");
		table.getColumns().add("cola", col->{
			col.setDataType(DataType.INT);
		});
		table.getColumns().add("colb", col->{
			col.setDataType(DataType.BIGINT);
		});
		table.getColumns().add("colc", col->{
			col.setDataType(DataType.VARCHAR).setLength(50)
			.setCharacterSet("utf8").setCollation("utf8mb4_binary");
		});
		table.getColumns().add("cold", col->{
			col.setDataType(DataType.DATETIME);
		});
		table.getColumns().add("cole",col->{
			col.setDataType(DataType.ENUM).setDataTypeName(
					"enum('a', 'b', 'c')");
		});
		//
		Index index=new Index("indexa");
		index.setIndexType(IndexType.Hash);
		table.getIndexes().add(index);
		index.getColumns().add("colc", col->{
			col.setNullsOrder(NullsOrder.NullsLast);
			col.setOrder(Order.Desc);
		});
		index.getColumns().add("cold", col->{
			col.setIncludedColumn(true);
		});
		index.getSpecifics().put("fillfactor",50);
		index.getSpecifics().put("buffering","off");
		index.setRemarks("idx comment");
		//
		index.setWhere("cold>2");
		ExcludeConstraint excludeConstraint=new ExcludeConstraint("exc1");
		excludeConstraint.addColumn("colb", "&&");
		table.getConstraints().add(excludeConstraint);
		return table;
	}
}
