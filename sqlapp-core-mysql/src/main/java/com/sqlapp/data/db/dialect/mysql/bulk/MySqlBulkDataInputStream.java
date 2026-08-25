/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.bulk;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.util.CommonUtils;

/** Streams rows for MySQL LOAD DATA LOCAL INFILE without buffering the table. */
public class MySqlBulkDataInputStream extends InputStream {
	static final char RECORD_TERMINATOR = '\u001e';
	static final char DELIMITER = '\u001f';

	private final List<Column> columns = new ArrayList<>();
	private final Iterator<Row> rows;
	private byte[] current = new byte[0];
	private int position;
	private long rowCount;
	private boolean closed;

	public MySqlBulkDataInputStream(final Table table, final BulkOption options) {
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
			throw new IllegalArgumentException(
					"No writable MySQL bulk columns: " + table.getName());
		}
		rows = table.getRows().iterator();
	}

	public List<Column> getColumns() {
		return java.util.Collections.unmodifiableList(columns);
	}

	public long getRowCount() {
		return rowCount;
	}

	@Override
	public int read() throws IOException {
		final byte[] one = new byte[1];
		return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
	}

	@Override
	public int read(final byte[] buffer, final int offset, final int length)
			throws IOException {
		java.util.Objects.checkFromIndexSize(offset, length, buffer.length);
		if (closed) {
			throw new IOException("Stream is closed");
		}
		if (length == 0) {
			return 0;
		}
		int written = 0;
		while (written < length) {
			if (position >= current.length) {
				if (!rows.hasNext()) {
					return written == 0 ? -1 : written;
				}
				current = encode(rows.next()).getBytes(StandardCharsets.UTF_8);
				position = 0;
				rowCount++;
			}
			final int count = Math.min(length - written, current.length - position);
			System.arraycopy(current, position, buffer, offset + written, count);
			position += count;
			written += count;
		}
		return written;
	}

	private String encode(final Row row) {
		final StringBuilder builder = new StringBuilder();
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) {
				builder.append(DELIMITER);
			}
			final Object value = row.get(columns.get(i).getOrdinal());
			if (value == null) {
				builder.append("\\N");
			} else {
				final String text = value instanceof byte[] bytes
						? java.util.HexFormat.of().formatHex(bytes)
						: value.toString();
				appendEscaped(builder, text);
			}
		}
		return builder.append(RECORD_TERMINATOR).toString();
	}

	private void appendEscaped(final StringBuilder builder, final String text) {
		for (int i = 0; i < text.length(); i++) {
			final char character = text.charAt(i);
			if (character == '\\' || character == DELIMITER
					|| character == RECORD_TERMINATOR) {
				builder.append('\\');
			}
			builder.append(character);
		}
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
				throw new IOException("Failed to close MySQL bulk rows", e);
			}
		}
	}
}
