/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.sqlapp.data.db.dialect.mdb.DialectHolder;
import com.sqlapp.data.db.dialect.mdb.MdbFileLoader;
import com.sqlapp.data.schemas.Order;

import io.github.spannm.jackcess.ColumnBuilder;
import io.github.spannm.jackcess.DataType;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;
import io.github.spannm.jackcess.IndexBuilder;
import io.github.spannm.jackcess.TableBuilder;

class MdbMetadataReaderTest {

	@TempDir
	Path tempDirectory;

	@ParameterizedTest
	@EnumSource(value = Database.FileFormat.class, names = { "V2000", "V2010" })
	void physicalPrimaryKeysAndIndexesRetainNamesAndOrder(final Database.FileFormat format) throws Exception {
		final Path path = createDatabase(format);
		try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + path.toAbsolutePath())) {
			verifyMetadata(connection);
		}
	}

	@ParameterizedTest
	@EnumSource(value = Database.FileFormat.class, names = { "V2000", "V2010" })
	void wrappedConnectionsReadPhysicalMetadataWithoutPerTableJdbcKeyQueries(final Database.FileFormat format)
			throws Exception {
		final Path path = createDatabase(format);
		try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + path.toAbsolutePath())) {
			final AtomicInteger keyQueries = new AtomicInteger();
			final DatabaseMetaData metadata = (DatabaseMetaData) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { DatabaseMetaData.class }, (proxy, method, args) -> {
						if (method.getName().equals("getPrimaryKeys") || method.getName().equals("getIndexInfo")) {
							keyQueries.incrementAndGet();
						}
						return invoke(method, connection.getMetaData(), args);
					});
			final Connection wrapped = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { Connection.class }, (proxy, method, args) -> {
						return switch (method.getName()) {
						case "isWrapperFor" -> ((Class<?>) args[0]).isInstance(connection);
						case "unwrap" -> ((Class<?>) args[0]).cast(connection);
						case "getMetaData" -> metadata;
						default -> invoke(method, connection, args);
						};
					});
			verifyMetadata(wrapped);
			assertEquals(0, keyQueries.get(), "Physical keys and indexes must not query the SQL mirror per table");
		}
	}

	private void verifyMetadata(final Connection connection) {
		final var primaryKeys = new MdbPrimaryKeyConstraintReader(DialectHolder.defaultDialect);
		final var allKeys = primaryKeys.getAllFull(connection);
		assertEquals(2, allKeys.size());
		primaryKeys.setObjectName("受注 Table");
		final var key = primaryKeys.getAllFull(connection).getFirst();
		assertEquals("複合 主キー", key.getName());
		assertEquals("受注 Table", key.getTableName());
		assertTrue(key.isPrimaryKey());
		assertEquals(List.of("ID B", "ID A"), key.getColumns().stream().map(column -> column.getName()).toList());
		primaryKeys.setConstraintName("missing");
		assertTrue(primaryKeys.getAllFull(connection).isEmpty());
		primaryKeys.setConstraintName("複合 主キー");
		assertEquals(1, primaryKeys.getAllFull(connection).size());
		primaryKeys.setConstraintName(null);
		primaryKeys.setObjectName("No Key");
		assertTrue(primaryKeys.getAllFull(connection).isEmpty());
		primaryKeys.setObjectName("Missing Table");
		assertTrue(primaryKeys.getAllFull(connection).isEmpty());

		final var indexes = new MdbIndexReader(DialectHolder.defaultDialect);
		indexes.setObjectName("受注 Table");
		assertEquals(2, indexes.getAllFull(connection).size());
		indexes.setIndexName("idx desc");
		final var ordered = indexes.getAllFull(connection);
		assertEquals(1, ordered.size());
		assertFalse(ordered.getFirst().isUnique());
		assertEquals(List.of("ID A", "ID B"),
				ordered.getFirst().getColumns().stream().map(column -> column.getName()).toList());
		assertEquals(Order.Desc, ordered.getFirst().getColumns().get(0).getOrder());
		assertEquals(Order.Asc, ordered.getFirst().getColumns().get(1).getOrder());
		indexes.setIndexName("IDX Unique");
		final var unique = indexes.getAllFull(connection).getFirst();
		assertTrue(unique.isUnique());
		assertTrue(unique.getSpecifics().get(MdbFileLoader.INDEX_IGNORE_NULLS, Boolean.class));
		indexes.setIndexName("missing");
		assertTrue(indexes.getAllFull(connection).isEmpty());

		final var tables = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		tables.setObjectName("受注 Table");
		final var table = tables.getAllFull(connection).getFirst();
		assertEquals("複合 主キー", table.getConstraints().getPrimaryKeyConstraint().getName());
		assertEquals(List.of("ID B", "ID A"), table.getConstraints().getPrimaryKeyConstraint().getColumns().stream()
				.map(column -> column.getName()).toList());
		assertEquals(2, table.getIndexes().size());
		assertEquals("名称", table.getIndexes().get("IDX Unique").getColumns().getFirst().getColumn().getName());
	}

	private Path createDatabase(final Database.FileFormat format) throws Exception {
		final Path path = tempDirectory.resolve("metadata" + format.getFileExtension());
		try (Database database = DatabaseBuilder.create(format, path.toFile())) {
			new TableBuilder("受注 Table").addColumn(new ColumnBuilder("ID A", DataType.LONG))
					.addColumn(new ColumnBuilder("ID B", DataType.LONG))
					.addColumn(new ColumnBuilder("名称", DataType.TEXT).withLengthInUnits(40))
					.addIndex(new IndexBuilder("複合 主キー").withColumns("ID B", "ID A").withPrimaryKey())
					.addIndex(new IndexBuilder("IDX Desc").withColumns(false, "ID A").withColumns("ID B"))
					.addIndex(new IndexBuilder("IDX Unique").withColumns("名称").withUnique().withIgnoreNulls())
					.toTable(database);
			new TableBuilder("Other Table").addColumn(new ColumnBuilder("ID", DataType.LONG))
					.addIndex(new IndexBuilder("Original Primary").withColumns("ID").withPrimaryKey())
					.toTable(database);
			new TableBuilder("No Key").addColumn(new ColumnBuilder("ID", DataType.LONG)).toTable(database);
		}
		return path;
	}

	private Object invoke(final Method method, final Object target, final Object[] args) throws Throwable {
		try {
			return method.invoke(target, args);
		} catch (final InvocationTargetException e) {
			throw e.getCause();
		}
	}
}
