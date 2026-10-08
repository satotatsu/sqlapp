/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-hsql.
 */
package com.sqlapp.data.db.dialect.hsql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** HSQLDB integration coverage for ordered batch generated-key propagation. */
class HsqlJdbcTreeDataSessionTest {

	@Test
	void testSequencePreallocationVersionBoundary() {
		assertFalse(DialectHolder.defaultDialect.supportsSequencePreallocation());
		assertTrue(DialectHolder.defaultDialect2_0_0.supportsSequencePreallocation());
		assertTrue(DialectHolder.defaultDialect2_4_0.supportsSequencePreallocation());
	}

	@Test
	void testSequencesAreAllocatedPerRootBatchAndPropagateToChildren() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:tree-sequences", "SA", "")) {
			connection.setAutoCommit(false);
			try (Statement statement = connection.createStatement()) {
				statement.execute("CREATE SEQUENCE parent_seq AS BIGINT START WITH 10 INCREMENT BY 3");
				statement.execute("CREATE SEQUENCE child_seq AS BIGINT START WITH 100 INCREMENT BY 2");
				statement.execute("CREATE TABLE parent_table (id BIGINT PRIMARY KEY, txt VARCHAR(256))");
				statement.execute(
						"CREATE TABLE child_table (id BIGINT PRIMARY KEY, parent_id BIGINT NOT NULL REFERENCES parent_table(id), txt VARCHAR(256))");
			}
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC", "PARENT_TABLE", "CHILD_TABLE").orElseThrow();
			Table parent = schema.getTables().get("PARENT_TABLE");
			Table child = schema.getTables().get("CHILD_TABLE");
			schema.getSequences().add(new Sequence("PARENT_SEQ"));
			schema.getSequences().add(new Sequence("CHILD_SEQ"));
			parent.getColumns().get("ID").setSequenceName("PARENT_SEQ");
			child.getColumns().get("ID").setSequenceName("CHILD_SEQ");
			List<String> allocations = new ArrayList<>();
			AtomicInteger sequenceStatements = new AtomicInteger();
			Connection observed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
					new Class<?>[] { Connection.class }, (proxy, method, args) -> {
						try {
							Object result = method.invoke(connection, args);
							if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
									&& sql.contains("NEXT VALUE FOR")) {
								sequenceStatements.incrementAndGet();
								PreparedStatement statement = (PreparedStatement) result;
								AtomicInteger size = new AtomicInteger();
								return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
										new Class<?>[] { PreparedStatement.class }, (p, m, a) -> {
											if (m.getName().equals("setInt")) {
												size.set((Integer) a[1]);
											}
											if (m.getName().equals("executeQuery")) {
												allocations.add((sql.contains("PARENT_SEQ") ? "parent:" : "child:")
														+ size.get());
											}
											try {
												return m.invoke(statement, a);
											} catch (InvocationTargetException e) {
												throw e.getCause();
											}
										});
							}
							return result;
						} catch (InvocationTargetException e) {
							throw e.getCause();
						}
					});
			new JdbcTreeDataSession(observed, parent, child).execute(session -> {
				session.setRootBatchSize(3);
				session.setTableOperationMode(TableOperationMode.INSERT);
				for (int i = 0; i < 5; i++) {
					session.newRow(parent).put("TXT", "parent-" + i);
					for (int j = 0; j < 2; j++) {
						session.newRow(child).put("TXT", "child-" + i + "-" + j);
					}
				}
			});
			assertEquals(List.of("parent:3", "child:6", "parent:2", "child:4"), allocations);
			assertEquals(2, sequenceStatements.get());
			try (Statement statement = connection.createStatement();
					ResultSet rows = statement.executeQuery(
							"SELECT p.id, c.id, c.parent_id FROM parent_table p JOIN child_table c ON c.parent_id=p.id ORDER BY c.id")) {
				for (int i = 0; i < 10; i++) {
					assertTrue(rows.next());
					assertEquals(10 + 3 * (i / 2), rows.getLong(1));
					assertEquals(100 + 2 * i, rows.getLong(2));
					assertEquals(rows.getLong(1), rows.getLong(3));
				}
				assertFalse(rows.next());
			}
		}
	}

	@Test
	void testBatchGeneratedKeysPropagateAndPreparedStatementsAreReused() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:tree-data-session", "SA", "")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC", "PARENT_TABLE", "CHILD_TABLE").orElseThrow();
			Table parent = schema.getTables().get("PARENT_TABLE");
			Table child = schema.getTables().get("CHILD_TABLE");
			Set<PreparedStatement> statements = Collections.newSetFromMap(new IdentityHashMap<>());
			AtomicInteger executions = new AtomicInteger();

			new JdbcTreeDataSession(connection, parent, child).execute(session -> {
				session.setRootBatchSize(3);
				session.setTableOperationMode(TableOperationMode.INSERT);
				session.setPreparedStatementBeforeExecuteHandler(statement -> {
					statements.add(statement);
					executions.incrementAndGet();
				});
				for (int i = 1; i <= 5; i++) {
					Row parentRow = session.newRow(parent);
					parentRow.put("TXT", "parent-" + i);
					Row childRow = session.newRow(child);
					childRow.put("TXT", "child-" + i);
				}
			});

			assertEquals(2, statements.size());
			assertEquals(4, executions.get());
			try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("""
					SELECT p.id, p.txt, c.parent_id, c.txt
					FROM parent_table p
					JOIN child_table c ON c.parent_id = p.id
					ORDER BY p.id
					""")) {
				for (long i = 0; i < 5; i++) {
					assertTrue(resultSet.next());
					assertEquals(i, resultSet.getLong(1));
					assertEquals("parent-" + (i + 1), resultSet.getString(2));
					assertEquals(i, resultSet.getLong(3));
					assertEquals("child-" + (i + 1), resultSet.getString(4));
				}
				assertFalse(resultSet.next());
			}
		}
	}

	private void createTables(final Connection connection) throws Exception {
		try (Statement statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE parent_table (
						id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
						txt VARCHAR(256)
					)
					""");
			statement.execute("""
					CREATE TABLE child_table (
						id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
						parent_id BIGINT NOT NULL,
						txt VARCHAR(256),
						CONSTRAINT fk_child_parent FOREIGN KEY (parent_id) REFERENCES parent_table(id)
					)
					""");
			connection.commit();
		}
	}
}
