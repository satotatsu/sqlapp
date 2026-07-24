/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.command.jdbc.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.test.AbstractDbCommandTest;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;
import com.zaxxer.hikari.HikariDataSource;

class JdbcTreeDataSessionDeleteReplaceTest extends AbstractDbCommandTest {

	private static final String CREATE_PARENT = """
			CREATE TABLE PARENT_TABLE
			(
				  ID INTEGER
				, TXT VARCHAR(256)
				, PRIMARY KEY (ID)
			)""";

	private static final String CREATE_CHILD = """
			CREATE TABLE CHILD_TABLE
			(
				  ID INTEGER
				, PARENT_ID INTEGER NOT NULL
				, TXT VARCHAR(256)
				, PRIMARY KEY (ID)
				, FOREIGN KEY (PARENT_ID) REFERENCES PARENT_TABLE (ID)
			)""";

	@Test
	void testDeleteChildren() throws SQLException {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			Schema schema = createSchema(connection);
			Table parentTable = schema.getTables().get("PARENT_TABLE");
			Table childTable = schema.getTables().get("CHILD_TABLE");

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, schema.getTables())) {
				session.setTableOperationMode(table -> table == childTable ? TableOperationMode.DELETE
						: TableOperationMode.NONE);

				Row parent = session.newRow(parentTable);
				parent.put("ID", 1);

				Row child = session.newRow(childTable);
				child.put("ID", 11);
			}

			parentTable.read(connection);
			childTable.read(connection);
			assertEquals(2, parentTable.getRows().size());
			assertEquals(2, childTable.getRows().size());
			assertTrue(childTable.getRows().stream().noneMatch(row -> Integer.valueOf(11).equals(row.get("ID"))));
			assertEquals(Integer.valueOf(1),
					childTable.getRows().find(row -> Integer.valueOf(12).equals(row.get("ID"))).get("PARENT_ID"));
			assertEquals(Integer.valueOf(2),
					childTable.getRows().find(row -> Integer.valueOf(21).equals(row.get("ID"))).get("PARENT_ID"));
		}
	}

	@Test
	void testReplaceChildrenByRootRows() throws SQLException {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			Schema schema = createSchema(connection);
			Table parentTable = schema.getTables().get("PARENT_TABLE");
			Table childTable = schema.getTables().get("CHILD_TABLE");

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, schema.getTables())) {
				session.setTableOperationMode(table -> table == childTable ? TableOperationMode.REPLACE
						: TableOperationMode.UPDATE);

				Row parent = session.newRow(parentTable);
				parent.put("ID", 1);
				parent.put("TXT", "parent-1-replaced");

				Row child = session.newRow(childTable);
				child.put("ID", 13);
				child.put("TXT", "child-13");
				child = session.newRow(childTable);
				child.put("ID", 14);
				child.put("TXT", "child-14");
			}

			parentTable.read(connection);
			childTable.read(connection);
			assertEquals(2, parentTable.getRows().size());
			assertEquals("parent-1-replaced", parentTable.getRows().find(row -> Integer.valueOf(1).equals(row.get("ID")))
					.get("TXT"));
			assertEquals(3, childTable.getRows().size());
			assertTrue(childTable.getRows().stream().noneMatch(row -> Integer.valueOf(11).equals(row.get("ID"))));
			assertTrue(childTable.getRows().stream().noneMatch(row -> Integer.valueOf(12).equals(row.get("ID"))));
			assertEquals(Integer.valueOf(1),
					childTable.getRows().find(row -> Integer.valueOf(13).equals(row.get("ID"))).get("PARENT_ID"));
			assertEquals(Integer.valueOf(1),
					childTable.getRows().find(row -> Integer.valueOf(14).equals(row.get("ID"))).get("PARENT_ID"));
			assertEquals(Integer.valueOf(2),
					childTable.getRows().find(row -> Integer.valueOf(21).equals(row.get("ID"))).get("PARENT_ID"));
		}
	}

	private Schema createSchema(Connection connection) throws SQLException {
		dropTables(connection, "CHILD_TABLE");
		dropTables(connection, "PARENT_TABLE");
		executeSql(connection, CREATE_PARENT);
		executeSql(connection, CREATE_CHILD);
		executeSql(connection, "INSERT INTO PARENT_TABLE (ID, TXT) VALUES (1, 'parent-1')");
		executeSql(connection, "INSERT INTO PARENT_TABLE (ID, TXT) VALUES (2, 'parent-2')");
		executeSql(connection, "INSERT INTO CHILD_TABLE (ID, PARENT_ID, TXT) VALUES (11, 1, 'child-11')");
		executeSql(connection, "INSERT INTO CHILD_TABLE (ID, PARENT_ID, TXT) VALUES (12, 1, 'child-12')");
		executeSql(connection, "INSERT INTO CHILD_TABLE (ID, PARENT_ID, TXT) VALUES (21, 2, 'child-21')");
		Optional<Schema> schema = SchemaUtils.getSchema(connection, "PUBLIC");
		assertTrue(schema.isPresent());
		return schema.get();
	}
}
