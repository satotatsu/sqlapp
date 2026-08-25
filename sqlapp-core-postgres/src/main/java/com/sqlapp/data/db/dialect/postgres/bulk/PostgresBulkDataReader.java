/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.bulk;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Array;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.util.CommonUtils;

/** Streams Schema rows as PostgreSQL COPY CSV without buffering all rows. */
public class PostgresBulkDataReader extends Reader {
	private final List<Column> columns = new ArrayList<>();
	private final Iterator<Row> rows;
	private String current = "";
	private int position;
	private long rowCount;
	private boolean closed;

	public PostgresBulkDataReader(final Table table, final BulkOption options) {
		java.util.Objects.requireNonNull(table, "table");
		final BulkOption effective = options == null ? BulkOption.defaults() : options;
		for (final Column column : table.getColumns()) {
			if (column.isHidden() || !CommonUtils.isEmpty(column.getFormula())
					|| (column.isIdentity() && !effective.isKeepIdentity())) {
				continue;
			}
			columns.add(column);
		}
		if (columns.isEmpty()) {
			throw new IllegalArgumentException("No writable PostgreSQL COPY columns: "
					+ table.getName());
		}
		this.rows = table.getRows().iterator();
	}

	public List<Column> getColumns() {
		return java.util.Collections.unmodifiableList(columns);
	}

	public long getRowCount() {
		return rowCount;
	}

	@Override
	public int read(final char[] buffer, final int offset, final int length)
			throws IOException {
		java.util.Objects.checkFromIndexSize(offset, length, buffer.length);
		if (closed) {
			throw new IOException("Reader is closed");
		}
		if (length == 0) {
			return 0;
		}
		int written = 0;
		while (written < length) {
			if (position >= current.length()) {
				if (!rows.hasNext()) {
					return written == 0 ? -1 : written;
				}
				current = toCsv(rows.next());
				position = 0;
				rowCount++;
			}
			final int count = Math.min(length - written,
					current.length() - position);
			current.getChars(position, position + count, buffer,
					offset + written);
			position += count;
			written += count;
		}
		return written;
	}

	private String toCsv(final Row row) {
		final StringBuilder builder = new StringBuilder();
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) {
				builder.append(',');
			}
			final Object value = row.get(columns.get(i).getOrdinal());
			if (value != null) {
				appendCsv(builder, toText(value));
			}
		}
		return builder.append('\n').toString();
	}

	private String toText(final Object value) {
		if (value instanceof byte[] bytes) {
			final StringBuilder builder = new StringBuilder(2 + bytes.length * 2);
			builder.append("\\x");
			for (final byte currentByte : bytes) {
				builder.append(Character.forDigit((currentByte >>> 4) & 0x0f, 16));
				builder.append(Character.forDigit(currentByte & 0x0f, 16));
			}
			return builder.toString();
		}
		if (value.getClass().isArray()) {
			final StringBuilder builder = new StringBuilder("{");
			for (int i = 0; i < Array.getLength(value); i++) {
				if (i > 0) {
					builder.append(',');
				}
				final Object element = Array.get(value, i);
				if (element == null) {
					builder.append("NULL");
				} else {
					builder.append('"').append(element.toString()
							.replace("\\", "\\\\").replace("\"", "\\\""))
							.append('"');
				}
			}
			return builder.append('}').toString();
		}
		if (value instanceof TemporalAccessor) {
			return value.toString();
		}
		return value.toString();
	}

	private void appendCsv(final StringBuilder builder, final String value) {
		builder.append('"').append(value.replace("\"", "\"\"")).append('"');
	}

	@Override
	public void close() throws IOException {
		if (closed) {
			return;
		}
		closed = true;
		if (rows instanceof AutoCloseable closeable) {
			try {
				closeable.close();
			} catch (Exception e) {
				throw new IOException("Failed to close PostgreSQL COPY rows", e);
			}
		}
	}
}
