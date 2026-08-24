/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.mdb.rowiterator.MdbRowIteratorHandler;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;

/** Loads an Access MDB/ACCDB file without passing identifiers through SQL. */
public final class MdbFileLoader {

	private MdbFileLoader() {
	}

	public static Schema load(final Path file) throws IOException {
		return loadSchema(file);
	}

	public static Schema loadSchema(final Path file) throws IOException {
		final Path normalized = file.toAbsolutePath().normalize();
		final Schema schema = new Schema("");
		try (Database database = new DatabaseBuilder().withPath(normalized)
				.withReadOnly(true).open()) {
			for (final String tableName : database.getTableNames()) {
				final io.github.spannm.jackcess.Table source = database
						.getTable(tableName);
				if (source == null || source.isSystem()) {
					continue;
				}
				final Table table = toTable(source);
				table.setRowIteratorHandler(
						new MdbRowIteratorHandler(normalized, tableName));
				schema.getTables().add(table);
			}
			loadRelationships(database, schema);
		}
		return schema;
	}

	public static Table loadTable(final Path file, final String tableName)
			throws IOException {
		final Path normalized = file.toAbsolutePath().normalize();
		try (Database database = new DatabaseBuilder().withPath(normalized)
				.withReadOnly(true).open()) {
			final io.github.spannm.jackcess.Table source = database
					.getTable(tableName);
			if (source == null || source.isSystem()) {
				throw new IllegalArgumentException(
						"Access table not found: " + tableName);
			}
			final Table table = toTable(source);
			table.setRowIteratorHandler(
					new MdbRowIteratorHandler(normalized, tableName));
			return table;
		}
	}

	private static Table toTable(
			final io.github.spannm.jackcess.Table source) throws IOException {
		final Table table = new Table(source.getName());
		for (final io.github.spannm.jackcess.Column sourceColumn : source
				.getColumns()) {
			final Column column = new Column(sourceColumn.getName());
			column.setDataType(toDataType(sourceColumn.getType()));
			column.setDataTypeName(sourceColumn.getType().getTypeName());
			column.setIdentity(sourceColumn.isAutoNumber());
			if (sourceColumn.getType().isTextual()
					&& !sourceColumn.getType().isLongValue()) {
				column.setLength((long) sourceColumn.getLengthInUnits());
			}
			if (sourceColumn.getType().getHasScalePrecision()) {
				column.setLength((long) sourceColumn.getPrecision());
				column.setScale((int) sourceColumn.getScale());
			}
			table.getColumns().add(column);
		}
		for (final io.github.spannm.jackcess.Index sourceIndex : source
				.getIndexes()) {
			final List<Column> keyColumns = new ArrayList<>();
			for (final io.github.spannm.jackcess.Index.Column sourceColumn : sourceIndex
					.getColumns()) {
				keyColumns.add(table.getColumns().get(sourceColumn.getName()));
			}
			if (sourceIndex.isPrimaryKey()) {
				table.setPrimaryKey(sourceIndex.getName(),
						keyColumns.toArray(Column[]::new));
				continue;
			}
			final Index index = new Index(sourceIndex.getName());
			index.setUnique(sourceIndex.isUnique());
			for (int i = 0; i < sourceIndex.getColumns().size(); i++) {
				final io.github.spannm.jackcess.Index.Column sourceColumn = sourceIndex
						.getColumns().get(i);
				index.getColumns().add(new ReferenceColumn(keyColumns.get(i),
						sourceColumn.isAscending() ? Order.Asc : Order.Desc));
			}
			table.getIndexes().add(index);
		}
		return table;
	}

	private static void loadRelationships(final Database database,
			final Schema schema) throws IOException {
		for (final io.github.spannm.jackcess.Relationship relationship : database
				.getRelationships()) {
			final Table relatedTable = schema.getTables()
					.get(relationship.getFromTable().getName());
			final Table table = schema.getTables()
					.get(relationship.getToTable().getName());
			if (table == null || relatedTable == null) {
				continue;
			}
			final ForeignKeyConstraint foreignKey = new ForeignKeyConstraint(
					relationship.getName());
			foreignKey.setRelatedTableName(relatedTable.getName());
			final List<Column> columns = new ArrayList<>();
			for (final io.github.spannm.jackcess.Column sourceColumn : relationship
					.getToColumns()) {
				columns.add(table.getColumns().get(sourceColumn.getName()));
			}
			foreignKey.addColumns(columns);
			final List<Column> relatedColumns = new ArrayList<>();
			for (final io.github.spannm.jackcess.Column sourceColumn : relationship
					.getFromColumns()) {
				relatedColumns.add(
						relatedTable.getColumns().get(sourceColumn.getName()));
			}
			foreignKey.addRelatedColumns(relatedColumns);
			foreignKey.setUpdateRule(relationship.cascadeUpdates()
					? CascadeRule.Cascade : CascadeRule.None);
			foreignKey.setDeleteRule(relationship.cascadeDeletes()
					? CascadeRule.Cascade : CascadeRule.None);
			table.getConstraints().add(foreignKey);
		}
	}

	private static DataType toDataType(
			final io.github.spannm.jackcess.DataType type) {
		return switch (type) {
		case BOOLEAN -> DataType.BOOLEAN;
		case BYTE -> DataType.TINYINT;
		case INT -> DataType.SMALLINT;
		case LONG -> DataType.INT;
		case BIG_INT -> DataType.BIGINT;
		case MONEY, NUMERIC -> DataType.DECIMAL;
		case FLOAT -> DataType.REAL;
		case DOUBLE -> DataType.DOUBLE;
		case SHORT_DATE_TIME, EXT_DATE_TIME -> DataType.DATETIME;
		case BINARY -> DataType.VARBINARY;
		case TEXT, GUID -> DataType.NVARCHAR;
		case MEMO -> DataType.LONGNVARCHAR;
		case OLE, COMPLEX_TYPE -> DataType.BLOB;
		default -> DataType.OTHER;
		};
	}
}
