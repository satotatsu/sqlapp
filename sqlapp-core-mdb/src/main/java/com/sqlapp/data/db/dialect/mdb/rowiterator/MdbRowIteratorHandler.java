/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.rowiterator;

import java.nio.file.Path;
import java.sql.Blob;
import java.util.Iterator;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.rowiterator.AbstractRowIterator;
import com.sqlapp.data.schemas.rowiterator.AbstractRowIteratorHandler;

import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;

/** Lazily streams one Access table directly from its MDB/ACCDB file. */
public class MdbRowIteratorHandler extends AbstractRowIteratorHandler {

	private final Path file;
	private final String tableName;

	public MdbRowIteratorHandler(final Path file, final String tableName) {
		super((row, column, value) -> value);
		this.file = file.toAbsolutePath().normalize();
		this.tableName = tableName;
	}

	@Override
	public Iterator<Row> iterator(final RowCollection rows) {
		return new MdbIterator(rows, file, tableName);
	}

	private static class MdbIterator
			extends AbstractRowIterator<io.github.spannm.jackcess.Row> {

		private final Path file;
		private final String tableName;
		private Database database;
		private Iterator<io.github.spannm.jackcess.Row> iterator;

		MdbIterator(final RowCollection rows, final Path file,
				final String tableName) {
			super(rows, 0L, (row, column, value) -> value);
			this.file = file;
			this.tableName = tableName;
		}

		@Override
		protected void preInitialize() throws Exception {
			database = new DatabaseBuilder().withPath(file).withReadOnly(true)
					.open();
			final io.github.spannm.jackcess.Table source = database
					.getTable(tableName);
			if (source == null) {
				throw new IllegalArgumentException(
						"Access table not found: " + tableName);
			}
			iterator = source.iterator();
		}

		@Override
		protected void initializeColumn() {
		}

		@Override
		protected io.github.spannm.jackcess.Row read() {
			return iterator.next();
		}

		@Override
		protected boolean hasNextInternal() {
			return iterator.hasNext();
		}

		@Override
		protected void set(final io.github.spannm.jackcess.Row source,
				final Row row) {
			row.setDataSourceInfo(file.toString());
			row.setDataSourceDetailInfo(tableName);
			row.setDataSourceRowNumber(count);
			for (final Column column : table.getColumns()) {
				put(row, column, normalizeValue(column,
						source.get(column.getName())));
			}
		}

		private Object normalizeValue(final Column column, final Object value) {
			if (value == null || value instanceof byte[]) {
				return value;
			}
			if (value instanceof io.github.spannm.jackcess.complex.ComplexValueForeignKey) {
				throw new UnsupportedOperationException(
						"Access complex/attachment column is not supported: "
								+ tableName + "." + column.getName());
			}
			if (value instanceof Blob blob) {
				try {
					return blob.getBytes(1L, Math.toIntExact(blob.length()));
				} catch (final Exception e) {
					throw new RuntimeException("Failed to read Access OLE column: "
							+ tableName + "." + column.getName(), e);
				} finally {
					try {
						blob.free();
					} catch (final Exception e) {
						// The read result or original read failure remains primary.
					}
				}
			}
			return value;
		}

		@Override
		protected void doClose() {
			if (database != null) {
				try {
					database.close();
				} catch (final Exception e) {
					throw new RuntimeException("Failed to close Access database: "
							+ file, e);
				}
			}
		}
	}
}
