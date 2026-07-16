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
import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.SqlParser;

/**
 * For Nodeのテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class ParentRowsEqualsBindVariableNodeTest {
	private Dialect dialect = DialectResolver.getInstance().getDefaultDialect();

	/**
	 * ノード評価テスト
	 */
	@Test
	public void testEvalPRIMARY_KEY() {
		Table table = getTable();
		Table table2 = getTable2();
		String fkName = this.createForeignKey(table, table2);
		Node node = SqlParser.getInstance().parse(dialect, "/*PARENT_ROWS_EQUALS(" + fkName + ")*/");
		assertEquals(1, node.getChildNodes().size());
		ParentRowsEqualsBindVariableNode bindVariableNode = (ParentRowsEqualsBindVariableNode) node.getChildNodes()
				.get(0);
		assertEquals("/*PARENT_ROWS_EQUALS(" + fkName + ")*/", bindVariableNode.getSql());
		assertEquals(fkName, bindVariableNode.getForeignKeyName());
		SqlParameterCollection sqlParameterCollection = bindVariableNode.eval(table2);
		String exptected = """
				OR "colA" IN ( ?, ?, ? )
				""";
		assertEquals(exptected.trim(), sqlParameterCollection.getSql().trim());
		assertEquals(3, sqlParameterCollection.getParameterSize());
		List<BindParameter> bindParameters = sqlParameterCollection.getBindParameters().get(0).getBindParameters();
		int i = 0;
		int j = 0;
		assertEquals(3, bindParameters.size());
		BindParameter bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
	}

	/**
	 * ノード評価テスト
	 */
	@Test
	public void testEvalComplexPRIMARY_KEY() {
		Table table = getTable();
		Table table2 = getTable2();
		String fkName = this.createForeignKey(table, table2);
		Node node = SqlParser.getInstance().parse(dialect, "/*PARENT_ROWS_EQUALS(" + fkName + ")*/");
		assertEquals(1, node.getChildNodes().size());
		ParentRowsEqualsBindVariableNode bindVariableNode = (ParentRowsEqualsBindVariableNode) node.getChildNodes()
				.get(0);
		SqlParameterCollection sqlParameterCollection = bindVariableNode.eval(table2);
		String exptected = """
				OR (
					 ( "colA" = ? AND "colB" = ? )
					OR ( "colA" = ? AND "colB" = ? )
					OR ( "colA" = ? AND "colB" = ? )
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
		assertEquals((int) j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.VARCHAR, bindParameter.getDataType());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals((int) j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.VARCHAR, bindParameter.getDataType());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals((int) j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.VARCHAR, bindParameter.getDataType());
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
		String fkName = this.createForeignKey(table, table2);
		Node node = SqlParser.getInstance().parse(dialect, "/*PARENT_ROWS_EQUALS(" + fkName + ")*/");
		assertEquals(1, node.getChildNodes().size());
		ParentRowsEqualsBindVariableNode bindVariableNode = (ParentRowsEqualsBindVariableNode) node.getChildNodes()
				.get(0);
		SqlParameterCollection sqlParameterCollection = node.eval(table2);
		String exptected = """
				OR (
					 ( "colA", "colC" ) = ( ?, ? )
					OR ( "colA", "colC" ) = ( ?, ? )
					OR ( "colA", "colC" ) = ( ?, ? )
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
		assertEquals(DataType.DATETIME, bindParameter.getDataType());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.DATETIME, bindParameter.getDataType());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.DATETIME, bindParameter.getDataType());
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
		String fkName = this.createForeignKey(table, table2);
		Node node = SqlParser.getInstance().parse(dialect, "/*PARENT_ROWS_EQUALS(" + fkName + ")*/");
		assertEquals(1, node.getChildNodes().size());
		ParentRowsEqualsBindVariableNode bindVariableNode = (ParentRowsEqualsBindVariableNode) node.getChildNodes()
				.get(0);
		SqlParameterCollection sqlParameterCollection = node.eval(table2);
		String exptected = """
				OR ( "colA", "colB", "colC" ) IN ( ( ?, ?, ? ), ( ?, ?, ? ), ( ?, ?, ? ) )
				""";
		assertEquals(exptected.trim(), sqlParameterCollection.getSql().trim());
		assertEquals(9, sqlParameterCollection.getParameterSize());
		List<BindParameter> bindParameters = sqlParameterCollection.getBindParameters().get(0).getBindParameters();
		int i = 0;
		int j = 0;
		assertEquals(9, bindParameters.size());
		BindParameter bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.VARCHAR, bindParameter.getDataType());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.DATETIME, bindParameter.getDataType());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.VARCHAR, bindParameter.getDataType());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.DATETIME, bindParameter.getDataType());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getDataType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.VARCHAR, bindParameter.getDataType());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.DATETIME, bindParameter.getDataType());
	}

	private String createForeignKey(Table table, Table table2) {
		String fkName = "FK_" + table.getName();
		table.getConstraints().addForeignKeyConstraint(fkName, fk -> {
			fk.setColumns(table.getColumns().get("colAB"), table.getColumns().get("colAD"));
			fk.setRelatedColumns(table.getColumns().get("colBB"), table.getColumns().get("colBD"));
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
			row.put("colAB", "colB+" + i);
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
			row.put("colBB", "colB+" + i);
			row.put("colBC", LocalDateTime.now());
			row.put("colBD", i * 10);
			row.put("colBE", i * 3);
			table.getRows().add(row);
		}
		return table;
	}

}
