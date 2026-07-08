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
import com.sqlapp.data.schemas.SchemaUtils;
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
public class ValuesBindVariableNodeTest {
	private Dialect dialect = DialectResolver.getInstance().getDefaultDialect();

	/**
	 * ノード評価テスト
	 */
	@Test
	public void testEval() {
		Node node = SqlParser.getInstance().parse(dialect, "/*VALUES*/VALUES('Taro',20)/*END*/");
		assertEquals(1, node.getChildNodes().size());
		ValuesBindVariableNode valuesBindVariableArrayNode = (ValuesBindVariableNode) node.getChildNodes().get(0);
		assertEquals("/*VALUES*/VALUES", valuesBindVariableArrayNode.getSql());
		Table table = getTable();
		SqlParameterCollection sqlParameterCollection = node.eval(table);
		String exptected = """
				SELECT ?,?,? FROM (VALUES(0))
				UNION ALL
				SELECT ?,?,? FROM (VALUES(0))
				UNION ALL
				SELECT ?,?,? FROM (VALUES(0))
				""";
		assertEquals(exptected.trim(), sqlParameterCollection.getSql().trim());
		assertEquals(9, sqlParameterCollection.getParameterSize());
		List<BindParameter> bindParameters = sqlParameterCollection.getBindParameters().get(0).getBindParameters();
		int i = 0;
		int j = 0;
		assertEquals(9, bindParameters.size());
		BindParameter bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.VARCHAR, bindParameter.getType());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.DATETIME, bindParameter.getType());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.VARCHAR, bindParameter.getType());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.DATETIME, bindParameter.getType());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.VARCHAR, bindParameter.getType());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.DATETIME, bindParameter.getType());
	}

	/**
	 * ノード評価テスト
	 */
	@Test
	public void testEvalRowNumber() {
		Node node = SqlParser.getInstance().parse(dialect, "/*VALUES*/VALUES('Taro',20)/*END*/");
		assertEquals(1, node.getChildNodes().size());
		ValuesBindVariableNode valuesBindVariableArrayNode = (ValuesBindVariableNode) node.getChildNodes().get(0);
		assertEquals("/*VALUES*/VALUES", valuesBindVariableArrayNode.getSql());
		Table table = getTable(true);
		SqlParameterCollection sqlParameterCollection = node.eval(table);
		String exptected = """
				SELECT ?,?,?,? FROM (VALUES(0))
				UNION ALL
				SELECT ?,?,?,? FROM (VALUES(0))
				UNION ALL
				SELECT ?,?,?,? FROM (VALUES(0))
				""";
		assertEquals(exptected.trim(), sqlParameterCollection.getSql().trim());
		assertEquals(12, sqlParameterCollection.getParameterSize());
		List<BindParameter> bindParameters = sqlParameterCollection.getBindParameters().get(0).getBindParameters();
		int i = 0;
		int j = 0;
		assertEquals(12, bindParameters.size());
		BindParameter bindParameter = bindParameters.get(i++);
		assertEquals(DataType.BIGINT, bindParameter.getType());
		assertEquals((int) j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.VARCHAR, bindParameter.getType());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.DATETIME, bindParameter.getType());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.BIGINT, bindParameter.getType());
		assertEquals((int) j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.VARCHAR, bindParameter.getType());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.DATETIME, bindParameter.getType());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.BIGINT, bindParameter.getType());
		assertEquals((int) j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.VARCHAR, bindParameter.getType());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.DATETIME, bindParameter.getType());
	}

	private Dialect getDialect() {
		return DialectResolver.getInstance().getDialect("", 0, 0, 0);
	}

	private Dialect getCustomDialect() {
		Dialect dialect = new Dialect(() -> null) {
			@Override
			public boolean supportsValues() {
				return true;
			}
		};
		return dialect;
	}

	/**
	 * ノード評価テスト
	 */
	@Test
	public void testEval2() {
		Node node = SqlParser.getInstance().parse(getCustomDialect(), "/*VALUES*/VALUES('Taro',20)/*END*/");
		assertEquals(1, node.getChildNodes().size());
		ValuesBindVariableNode valuesBindVariableArrayNode = (ValuesBindVariableNode) node.getChildNodes().get(0);
		assertEquals("/*VALUES*/VALUES", valuesBindVariableArrayNode.getSql());
		Table table = getTable();
		SqlParameterCollection sqlParameterCollection = node.eval(table);
		String exptected = """
				VALUES
				  (?,?,?)
				, (?,?,?)
				, (?,?,?)
				""";
		assertEquals(exptected.trim(), sqlParameterCollection.getSql().trim());
		assertEquals(9, sqlParameterCollection.getParameterSize());
		List<BindParameter> bindParameters = sqlParameterCollection.getBindParameters().get(0).getBindParameters();
		int i = 0;
		int j = 0;
		assertEquals(9, bindParameters.size());
		BindParameter bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.VARCHAR, bindParameter.getType());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.DATETIME, bindParameter.getType());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.VARCHAR, bindParameter.getType());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.DATETIME, bindParameter.getType());
		j++;
		//
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.INT, bindParameter.getType());
		assertEquals(j, bindParameter.getValue());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.VARCHAR, bindParameter.getType());
		bindParameter = bindParameters.get(i++);
		assertEquals(DataType.DATETIME, bindParameter.getType());
	}

	private Table getTable() {
		return getTable(false);
	}

	private Table getTable(boolean rowNo) {
		Table table = new Table("tabA");
		table.getColumns().add(c -> {
			c.setName("colA");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("colB");
			c.setDataType(DataType.VARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colC");
			c.setDataType(DataType.DATETIME);
		});
		for (int i = 0; i < 3; i++) {
			Row row = table.newRow();
			if (rowNo) {
				SchemaUtils.setInternalRowId(row, i);
			}
			row.put("colA", i);
			row.put("colB", "colB+" + i);
			row.put("colC", LocalDateTime.now());
			table.getRows().add(row);
		}
		return table;
	}

}
