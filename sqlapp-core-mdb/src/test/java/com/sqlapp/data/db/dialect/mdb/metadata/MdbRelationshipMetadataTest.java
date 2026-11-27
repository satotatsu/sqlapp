/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.sqlapp.data.db.dialect.mdb.DialectHolder;
import com.sqlapp.data.db.dialect.mdb.MdbFileLoader;
import com.sqlapp.data.db.dialect.mdb.MdbJackcessSupport;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Schema;

import io.github.spannm.jackcess.ColumnBuilder;
import io.github.spannm.jackcess.DataType;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;
import io.github.spannm.jackcess.IndexBuilder;
import io.github.spannm.jackcess.RelationshipBuilder;
import io.github.spannm.jackcess.TableBuilder;

class MdbRelationshipMetadataTest {

	@TempDir
	Path directory;

	@ParameterizedTest
	@EnumSource(value = Database.FileFormat.class, names = { "V2000", "V2010" })
	void relationshipRulesSurviveJdbcFileLoadAndRebuild(final Database.FileFormat format) throws Exception {
		final Path source = createDatabase(format);
		final byte[] before = Files.readAllBytes(source);
		final Schema schema = MdbFileLoader.load(source);
		verify(schema);
		final Path target = directory.resolve("rebuilt" + format.getFileExtension());
		MdbJackcessSupport.rebuild(source, target, schema);
		assertArrayEquals(before, Files.readAllBytes(source));
		verify(MdbFileLoader.load(target));
		try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + target.toAbsolutePath())) {
			final var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader();
			verify(reader.getAllFull(connection).getFirst());
			final var keys = reader.getTableReader().getForeignKeyConstraintReader();
			assertEquals(2, keys.getAllFull(connection).size());
			keys.setObjectName("子 Table");
			keys.setConstraintName("fk 複合");
			final var selected = keys.getAllFull(connection);
			assertEquals(1, selected.size());
			assertEquals("子 Table", selected.getFirst().getColumns().getFirst().getTableName());
			assertEquals("親 Table", selected.getFirst().getRelatedTable().getName());
			keys.setConstraintName("missing");
			assertTrue(keys.getAllFull(connection).isEmpty());
			// 5.2.0 loads an existing SET NULL relationship as NO ACTION in its SQL mirror.
			try (var statement = connection.createStatement()) {
				assertThrows(SQLException.class,
						() -> statement.executeUpdate("DELETE FROM [親 Table] WHERE [ID A] = 1"));
			}
		}
		try (MdbJackcessSupport writer = MdbJackcessSupport.open(target)) {
			assertEquals(1, writer.deleteRowsByPrimaryKey("親 Table", List.of(Map.of("ID A", 1, "ID B", 2))));
		}
		try (Database database = new DatabaseBuilder().withPath(target).withReadOnly(true).open()) {
			final var child = database.getTable("子 Table").iterator().next();
			assertNull(child.get("P A"));
			assertNull(child.get("P B"));
			assertEquals(1, database.getTable("Loose").getRowCount());
		}
	}

	@ParameterizedTest
	@EnumSource(value = Database.FileFormat.class, names = { "V2000", "V2010" })
	void rebuildRejectsUnsupportedRulesAndRemovesIncompleteTarget(final Database.FileFormat format) throws Exception {
		final Path source = createDatabase(format);
		final var schema = MdbFileLoader.load(source);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var loose = schema.getTables().get("Loose");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(loose, SqlType.CREATE));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(loose.getConstraints().get("FK Loose"), SqlType.CREATE));
		final var key = (ForeignKeyConstraint) schema.getTables().get("子 Table").getConstraints().get("FK 複合");
		key.setDeleteRule(CascadeRule.SetDefault);
		final Path target = directory.resolve("invalid" + format.getFileExtension());
		final var error = assertThrows(IllegalArgumentException.class,
				() -> MdbJackcessSupport.rebuild(source, target, schema));
		assertTrue(error.getMessage().contains("SET DEFAULT"));
		assertFalse(Files.exists(target));
	}

	@ParameterizedTest
	@EnumSource(value = Database.FileFormat.class, names = { "V2000", "V2010" })
	void bulkMetadataUsesOneColumnsCallAndNoJdbcKeyQueries(final Database.FileFormat format) throws Exception {
		final Path file = createDatabase(format);
		try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + file.toAbsolutePath())) {
			final AtomicInteger columnCalls = new AtomicInteger();
			final AtomicInteger keyCalls = new AtomicInteger();
			final DatabaseMetaData metadata = (DatabaseMetaData) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { DatabaseMetaData.class }, (proxy, method, args) -> {
						if (method.getName().equals("getColumns")) {
							columnCalls.incrementAndGet();
						} else if (List.of("getPrimaryKeys", "getImportedKeys", "getIndexInfo")
								.contains(method.getName())) {
							keyCalls.incrementAndGet();
						}
						return invoke(method, connection.getMetaData(), args);
					});
			final Connection wrapped = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { Connection.class }, (proxy, method, args) -> switch (method.getName()) {
					case "isWrapperFor" -> ((Class<?>) args[0]).isInstance(connection);
					case "unwrap" -> ((Class<?>) args[0]).cast(connection);
					case "getMetaData" -> metadata;
					default -> invoke(method, connection, args);
					});
			final var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			final var tables = reader.getAllFull(wrapped);
			assertEquals(3, tables.size());
			assertEquals(1, columnCalls.get());
			assertEquals(0, keyCalls.get());
			reader.setObjectName("子 Table");
			final var child = reader.getAllFull(wrapped).getFirst();
			assertEquals(2, child.getColumns().size());
			final var key = (ForeignKeyConstraint) child.getConstraints().get("FK 複合");
			assertNotNull(key);
			assertEquals("親 Table", key.getRelatedTable().getName());
			assertEquals(2, columnCalls.get(), "Each complete read must issue just one columns query");
			assertEquals(0, keyCalls.get());
		}
	}

	private void verify(final Schema schema) {
		final var key = (ForeignKeyConstraint) schema.getTables().get("子 Table").getConstraints().get("FK 複合");
		assertNotNull(key, schema.getTables().get("子 Table").getConstraints().toString());
		assertTrue(key.isEnable());
		assertEquals(CascadeRule.SetNull, key.getDeleteRule());
		assertEquals(CascadeRule.Cascade, key.getUpdateRule());
		assertEquals("親 Table", key.getRelatedTableName());
		assertEquals(List.of("P B", "P A"), key.getColumns().stream().map(column -> column.getName()).toList());
		assertEquals(List.of("ID B", "ID A"),
				key.getRelatedColumns().stream().map(column -> column.getName()).toList());
		assertSame(schema.getTables().get("親 Table").getColumns().get("ID B"),
				key.getRelatedColumns().getFirst().getColumn());
		assertSame(schema.getTables().get("子 Table").getColumns().get("P B"), key.getColumns().getFirst());
		final var loose = (ForeignKeyConstraint) schema.getTables().get("Loose").getConstraints().get("FK Loose");
		assertNotNull(loose);
		assertFalse(loose.isEnable());
		assertEquals(CascadeRule.None, loose.getDeleteRule());
	}

	private Path createDatabase(final Database.FileFormat format) throws Exception {
		final Path file = directory.resolve("relationships" + format.getFileExtension());
		try (Database database = DatabaseBuilder.create(format, file.toFile())) {
			final var parent = new TableBuilder("親 Table").addColumn(new ColumnBuilder("ID A", DataType.LONG))
					.addColumn(new ColumnBuilder("ID B", DataType.LONG))
					.addIndex(new IndexBuilder("PK Parent").withColumns("ID B", "ID A").withPrimaryKey())
					.toTable(database);
			final var child = new TableBuilder("子 Table").addColumn(new ColumnBuilder("P A", DataType.LONG))
					.addColumn(new ColumnBuilder("P B", DataType.LONG)).toTable(database);
			final var loose = new TableBuilder("Loose").addColumn(new ColumnBuilder("P A", DataType.LONG))
					.addColumn(new ColumnBuilder("P B", DataType.LONG)).toTable(database);
			new RelationshipBuilder(parent, child).addColumns("ID B", "P B").addColumns("ID A", "P A").withName("FK 複合")
					.withReferentialIntegrity().withCascadeUpdates().withCascadeNullOnDelete().toRelationship(database);
			new RelationshipBuilder(parent, loose).addColumns("ID B", "P B").addColumns("ID A", "P A")
					.withName("FK Loose").toRelationship(database);
			parent.addRow(1, 2);
			child.addRow(1, 2);
			loose.addRow(10, 20);
		}
		return file;
	}

	private Object invoke(final Method method, final Object target, final Object[] args) throws Throwable {
		try {
			return method.invoke(target, args);
		} catch (final InvocationTargetException e) {
			throw e.getCause();
		}
	}
}
