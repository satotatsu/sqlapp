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

package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.test.AbstractDbCommandTest;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableRelationTreeHolder;
import com.sqlapp.data.schemas.function.SQLExceptionConsumer;
import com.zaxxer.hikari.HikariDataSource;

class JdbcBatchTreeInsertHandlerComplexPKTest extends AbstractDbCommandTest {

	private String CREATE_TABLE = """
			CREATE TABLE TAB
			(
				    PK_COL1 VARCHAR(256)
				  , PK_COL2 VARCHAR(256)
				  , TXT VARCHAR(256)
				  , CREATED_AT DATETIME NOT NULL
				  , PRIMARY KEY (PK_COL1, PK_COL2)
			)""";
	private String CREATE_TABLE_1 = """
			CREATE TABLE TAB_1
			(
				    PK_COL1 VARCHAR(256)
				  , PK_COL2 VARCHAR(256)
				  , PK_COL3 VARCHAR(256)
				  , TXT VARCHAR(256)
				  , CREATED_AT DATETIME NOT NULL
				  , PRIMARY KEY (PK_COL1, PK_COL2, PK_COL3)
				  , FOREIGN KEY(PK_COL1, PK_COL2) REFERENCES TAB (PK_COL1, PK_COL2)
			)""";
	private String CREATE_TABLE_1_1 = """
			CREATE TABLE TAB_1_1
			(
				    PK_COL1A VARCHAR(256)
				  , PK_COL2A VARCHAR(256)
				  , PK_COL3A VARCHAR(256)
				  , PK_COL4A VARCHAR(256)
				  , TXT VARCHAR(256)
				  , CREATED_AT DATETIME NOT NULL
				  , PRIMARY KEY (PK_COL1A, PK_COL2A, PK_COL3A, PK_COL4A)
				  , FOREIGN KEY (PK_COL1A, PK_COL2A, PK_COL3A) REFERENCES TAB_1 (PK_COL1, PK_COL2, PK_COL3)
			)""";

	/**
	 * Register data from multiple tables in a hierarchical structure using JDBC
	 * batch inserts, while automatically managing the hierarchy and setting foreign
	 * keys.
	 * 
	 * @throws SQLException
	 */
	@Test
	void testInsertUpdateWithCombinedPK() throws SQLException {
		test(connection -> {
			this.dropTables(connection, "TAB_2_1");
			this.dropTables(connection, "TAB_2");
			this.dropTables(connection, "TAB_1_1");
			this.dropTables(connection, "TAB_1");
			this.dropTables(connection, "TAB");
			executeSql(connection, CREATE_TABLE);
			executeSql(connection, CREATE_TABLE_1);
			executeSql(connection, CREATE_TABLE_1_1);
			Optional<Schema> schemaOption = SchemaUtils.getSchema(connection, "PUBLIC");
			assertTrue(schemaOption.isPresent());
			Schema schema = schemaOption.get();
			TableRelationTreeHolder tableRelationTreeHolder = new TableRelationTreeHolder(schema.getTables());
			JdbcBatchTreeInsertHandler handler = new JdbcBatchTreeInsertHandler(connection, tableRelationTreeHolder);
			handler.setNewRowInitializer(row -> {
				row.put("CREATED_AT", LocalDateTime.now());
			});
			handler.setRootBatchSize(2);
			handler.setCommitEveryRoots(3);
			boolean[] hasRootBatchSizeRows = new boolean[1];
			hasRootBatchSizeRows[0] = false;
			long[] batchCounterHolder = new long[1];
			long[] commitCounterHolder = new long[1];
			handler.setBeforeRootBatchHandler((batchCounter, table) -> {
				System.out.println("BeforeRootBatch batchCount=" + batchCounter);
				table.getRows().forEach(row -> System.out.println(row));
				assertTrue(handler.getRootBatchSize() >= table.getRows().size());
			});
			handler.setAfterRootBatchHandler((batchCounter, table) -> {
				System.out.println("AfterRootBatch batchCount=" + batchCounter);
				table.getRows().forEach(row -> System.out.println(row));
				assertTrue(handler.getRootBatchSize() >= table.getRows().size());
				if (handler.getRootBatchSize() == table.getRows().size()) {
					hasRootBatchSizeRows[0] = true;
				}
				batchCounterHolder[0] = batchCounter;
			});
			handler.setBeforeCommitEveryRootsHandler((commitCounter, row) -> {
				System.out.println("BeforeCommitEveryRoots commitCount=" + commitCounter);
				commitCounterHolder[0] = commitCounter;
			});
			handler.setAfterCommitEveryRootsHandler((commitCounter, row) -> {
				System.out.println("AfterCommitEveryRoots commitCount=" + commitCounter + ", lastRow=" + row);
				commitCounterHolder[0] = commitCounter;
			});
			final Table tab = schema.getTables().get("TAB");
			final Table tab1 = schema.getTables().get("TAB_1");
			final Table tab1_1 = schema.getTables().get("TAB_1_1");
			try (handler) {
				for (int i = 0; i < 10; i++) {
					Table current = tab;
					Row row = handler.newRow(current);
					row.put("PK_COL1", current.getName() + "_PK_COL1_" + i);
					row.put("PK_COL2", current.getName() + "_PK_COL2_" + i);
					row.put("TXT", current.getName() + "_TXT_" + i);
					for (int j = 0; j < 2; j++) {
						current = tab1;
						row = handler.newRow(current);
						row.put("PK_COL3", current.getName() + "_PK_COL3_" + j);// <- PK_COL1, PK_COL2 are inherited
																				// automatically.
						row.put("TXT", current.getName() + "_TXT_" + j);
						for (int k = 0; k < 3; k++) {
							current = tab1_1;
							row = handler.newRow(current);
							row.put("PK_COL4A", current.getName() + "_PK_COL4A_" + k);// <-PK_COL1A, PK_COL2A, PK_COL3A
																						// are inherited automatically.
							row.put("TXT", current.getName() + "_TXT_" + k);
						}
					}
				}
			}
			assertEquals(0, tab.getRows().size());
			assertEquals(0, tab1.getRows().size());
			assertEquals(0, tab1_1.getRows().size());
			Table table = tab;
			table.read(connection);
			int i = 0;
			assertEquals(10, table.getRows().size());
			for (Row row : table.getRows()) {
				System.out.println(row);
				assertEquals(table.getName() + "_PK_COL1_" + i, row.get("PK_COL1"));
				assertEquals(table.getName() + "_PK_COL2_" + i, row.get("PK_COL2"));
				assertEquals(table.getName() + "_TXT_" + i, row.get("TXT"));
				assertNotNull(row.get("CREATED_AT"));
				i++;
			}
			table = tab1;
			table.read(connection);
			assertEquals(20, table.getRows().size());
			i = 0;
			for (Row row : table.getRows()) {
				System.out.println(row);
				assertEquals(table.getName() + "_PK_COL3_" + (i % 2), row.get("PK_COL3"));
				assertEquals(table.getName() + "_TXT_" + (i % 2), row.get("TXT"));
				assertNotNull(row.get("CREATED_AT"));
				Row parentRow = tab1.getRows().find(r -> {
					return Objects.equals(r.get("PK_COL1"), row.get("PK_COL1"))
							&& Objects.equals(r.get("PK_COL2"), row.get("PK_COL2"));
				});
				assertEquals((String) parentRow.get("PK_COL1"), (String) row.get("PK_COL1"));
				assertEquals((String) parentRow.get("PK_COL2"), (String) row.get("PK_COL2"));
				i++;
			}
			table = tab1_1;
			table.read(connection);
			assertEquals(60, table.getRows().size());
			i = 0;
			for (Row row : table.getRows()) {
				System.out.println(row);
				assertEquals(table.getName() + "_PK_COL4A_" + (i % 3), row.get("PK_COL4A"));
				assertEquals(table.getName() + "_TXT_" + (i % 3), row.get("TXT"));
				assertNotNull(row.get("CREATED_AT"));
				Row parentRow = tab1.getRows().find(r -> {
					return Objects.equals(r.get("PK_COL1"), row.get("PK_COL1A"))
							&& Objects.equals(r.get("PK_COL2"), row.get("PK_COL2A"))
							&& Objects.equals(r.get("PK_COL3"), row.get("PK_COL3A"));
				});
				assertEquals((String) parentRow.get("PK_COL1"), (String) row.get("PK_COL1A"));
				assertEquals((String) parentRow.get("PK_COL2"), (String) row.get("PK_COL2A"));
				assertEquals((String) parentRow.get("PK_COL3"), (String) row.get("PK_COL3A"));
				i++;
			}
		}, (connection) -> {
			this.dropTables(connection, "TAB_1_1");
			this.dropTables(connection, "TAB_1");
			this.dropTables(connection, "TAB");
		});
	}

	private void test(SQLExceptionConsumer<Connection> cons, SQLExceptionConsumer<Connection> finCons)
			throws SQLException {
		try (HikariDataSource ds = newInternalDataSource(); Connection conn = ds.getConnection();) {
			cons.accept(conn);
			finCons.accept(conn);
		}
	}

}
