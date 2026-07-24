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

package com.sqlapp.jdbc.sql.node;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableRelationTreeHolder;
import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.SqlParser;
import com.sqlapp.util.CommonUtils;

/**
 * For Nodeのテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class RowsEqualsBindVariableNodeTest2 {
	private Dialect dialect = DialectResolver.getInstance().getDefaultDialect();

	/**
	 * ノード評価テスト
	 */
	@Test
	public void testEvalPRIMARY_KEY() {
		Table table = getTable();
		Table table2 = getTable2();
		createForeignKey(table, table2);
		List<Table> list = CommonUtils.list();
		list.add(table);
		list.add(table2);
		TableRelationTreeHolder tableRelationTreeHolder = new TableRelationTreeHolder(list);
		Node node = SqlParser.getInstance().parse(dialect, "/*ROWS_EQUALS(target=PARENT;prefix=a.)*/");
		assertEquals(1, node.getChildNodes().size());
		RowsEqualsBindVariableNode bindVariableNode = (RowsEqualsBindVariableNode) node.getChildNodes().get(0);
		assertEquals("/*PARENT_ROWS_EQUALS(PARENT)*/", bindVariableNode.getSql());
		assertEquals("PARENT", bindVariableNode.getTarget());
		SqlParameterCollection sqlParameterCollection = bindVariableNode
				.eval(tableRelationTreeHolder.getTableRelation(table), table.getRows());
		String exptected = """
				AND (
						 ( a."colBA" = ? AND a."colBE" = ? )
						OR ( a."colBA" = ? AND a."colBE" = ? )
						OR ( a."colBA" = ? AND a."colBE" = ? )
					)
								""";
		assertEquals(exptected.trim(), sqlParameterCollection.getSql().trim());
		assertEquals(6, sqlParameterCollection.getParameterSize());
		List<BindParameter> bindParameters = sqlParameterCollection.getBindParameters().get(0).getBindParameters();
		int i = 0;
		int j = 0;
		assertEquals(6, bindParameters.size());
		BindParameter bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j * 3, bindParameter.getValue());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j * 3, bindParameter.getValue());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j * 3, bindParameter.getValue());
	}

	/**
	 * ノード評価テスト
	 */
	@Test
	public void testEvalComplexPRIMARY_KEY() {
		Table table = getTable();
		Table table2 = getTable2();
		createForeignKey(table, table2);
		List<Table> list = CommonUtils.list();
		list.add(table);
		list.add(table2);
		TableRelationTreeHolder tableRelationTreeHolder = new TableRelationTreeHolder(list);
		Node node = SqlParser.getInstance().parse(dialect, "/*PARENT_ROWS_EQUALS(target=PARENT;prefix=a.)*/");
		assertEquals(1, node.getChildNodes().size());
		RowsEqualsBindVariableNode bindVariableNode = (RowsEqualsBindVariableNode) node.getChildNodes().get(0);
		SqlParameterCollection sqlParameterCollection = bindVariableNode
				.eval(tableRelationTreeHolder.getTableRelation(table), table.getRows());
		String exptected = """
				AND (
						 ( a."colBA" = ? AND a."colBE" = ? )
						OR ( a."colBA" = ? AND a."colBE" = ? )
						OR ( a."colBA" = ? AND a."colBE" = ? )
					)
					""";
		assertEquals(exptected.trim(), sqlParameterCollection.getSql().trim());
		assertEquals(6, sqlParameterCollection.getParameterSize());
		List<BindParameter> bindParameters = sqlParameterCollection.getBindParameters().get(0).getBindParameters();
		int i = 0;
		int j = 0;
		assertEquals(6, bindParameters.size());
		BindParameter bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j * 3, bindParameter.getValue());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j * 3, bindParameter.getValue());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j * 3, bindParameter.getValue());
	}

	/**
	 * ノード評価テスト
	 */
	@Test
	public void supportsRowValueComparison() {
		Dialect dialect = new Dialect(() -> null) {
			@Override
			public boolean supportsRowValueComparison() {
				return true;
			}
		};
		Table table = getTable();
		Table table2 = getTable2();
		createForeignKey(table, table2);
		List<Table> list = CommonUtils.list();
		list.add(table);
		list.add(table2);
		TableRelationTreeHolder tableRelationTreeHolder = new TableRelationTreeHolder(list);
		Node node = SqlParser.getInstance().parse(dialect, "/*PARENT_ROWS_EQUALS(target=PARENT)*/");
		assertEquals(1, node.getChildNodes().size());
		SqlParameterCollection sqlParameterCollection = node.eval(tableRelationTreeHolder.getTableRelation(table),
				table.getRows());
		String exptected = """
				AND (
						 ( "tabB"."colBA", "tabB"."colBE" ) = ( ?, ? )
						OR ( "tabB"."colBA", "tabB"."colBE" ) = ( ?, ? )
						OR ( "tabB"."colBA", "tabB"."colBE" ) = ( ?, ? )
					)
																												""";
		assertEquals(exptected.trim(), sqlParameterCollection.getSql().trim());
		assertEquals(6, sqlParameterCollection.getParameterSize());
		List<BindParameter> bindParameters = sqlParameterCollection.getBindParameters().get(0).getBindParameters();
		int i = 0;
		int j = 0;
		assertEquals(6, bindParameters.size());
		BindParameter bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j * 2, bindParameter.getValue());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j * 3, bindParameter.getValue());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j * 3, bindParameter.getValue());
	}

	/**
	 * ノード評価テスト
	 */
	@Test
	public void supportsRowValueComparisonIn() {
		Dialect dialect = new Dialect(() -> null) {
			@Override
			public boolean supportsRowValueComparisonIn() {
				return true;
			}
		};
		Table table = getTable();
		Table table2 = getTable2();
		createForeignKey(table, table2);
		List<Table> list = CommonUtils.list();
		list.add(table);
		list.add(table2);
		TableRelationTreeHolder tableRelationTreeHolder = new TableRelationTreeHolder(list);
		Node node = SqlParser.getInstance().parse(dialect, "/*PARENT_ROWS_EQUALS(PARENT)*/");
		assertEquals(1, node.getChildNodes().size());
		SqlParameterCollection sqlParameterCollection = node.eval(tableRelationTreeHolder.getTableRelation(table),
				table.getRows());
		String exptected = """
				AND ( "tabB"."colBA", "tabB"."colBE" ) IN ( ( ?, ? ), ( ?, ? ), ( ?, ? ) )
												""";
		assertEquals(exptected.trim(), sqlParameterCollection.getSql().trim());
		assertEquals(6, sqlParameterCollection.getParameterSize());
		List<BindParameter> bindParameters = sqlParameterCollection.getBindParameters().get(0).getBindParameters();
		int i = 0;
		int j = 0;
		assertEquals(6, bindParameters.size());
		BindParameter bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j * 3, bindParameter.getValue());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j * 3, bindParameter.getValue());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j * 3, bindParameter.getValue());
	}

	private String createForeignKey(Table table, Table table2) {
		String fkName = "FK_" + table.getName();
		table.getConstraints().addForeignKeyConstraint(fkName, fk -> {
			fk.setColumns(table.getColumns().get("colAB"), table.getColumns().get("colAD"));
			fk.setRelatedColumns(table2.getColumns().get("colBB"), table2.getColumns().get("colBD"));
		});
		return fkName;
	}

	private Table getTable() {
		Table table = new Table("tabA");
		table.getColumns().add(c -> {
			c.setName("colAA");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("colAB");
			c.setDataType(DataType.VARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colAC");
			c.setDataType(DataType.DATETIME);
		});
		table.getColumns().add(c -> {
			c.setName("colAD");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("colAE");
			c.setDataType(DataType.INT);
		});
		table.setPrimaryKey(table.getColumns().get("colAA"), table.getColumns().get("colAB"));
		for (int i = 0; i < 3; i++) {
			Row row = table.newRow();
			row.put("colAA", i);
			row.put("colAB", "colAB" + i);
			row.put("colAC", LocalDateTime.now());
			row.put("colAD", i * 10);
			row.put("colAE", i * 3);
			table.getRows().add(row);
		}
		return table;
	}

	private Table getTable2() {
		Table table = new Table("tabB");
		table.getColumns().add(c -> {
			c.setName("colBA");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("colBB");
			c.setDataType(DataType.VARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colBC");
			c.setDataType(DataType.DATETIME);
		});
		table.getColumns().add(c -> {
			c.setName("colBD");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("colBE");
			c.setDataType(DataType.INT);
		});
		table.setPrimaryKey(table.getColumns().get("colBA"), table.getColumns().get("colBE"));
		for (int i = 0; i < 3; i++) {
			Row row = table.newRow();
			row.put("colBA", i);
			row.put("colBB", "colBB" + i);
			row.put("colBC", LocalDateTime.now());
			row.put("colBD", i * 10);
			row.put("colBE", i * 3);
			table.getRows().add(row);
		}
		return table;
	}

}
