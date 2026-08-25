/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.bulk;

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

/** Streams Schema rows as UTF-8 CSV for Vertica COPY FROM STDIN. */
public class VirticaBulkDataInputStream extends InputStream {
	private final List<Column> columns = new ArrayList<>();
	private final Iterator<Row> rows;
	private byte[] current = new byte[0];
	private int position;
	private long rowCount;
	private boolean closed;

	public VirticaBulkDataInputStream(final Table table,
			final BulkOption options) {
		java.util.Objects.requireNonNull(table, "table");
		final BulkOption effective = options == null ? BulkOption.defaults()
				: options;
		for (final Column column : table.getColumns()) {
			if (column.isHidden() || !CommonUtils.isEmpty(column.getFormula())
					|| (column.isIdentity() && !effective.isKeepIdentity())) {
				continue;
			}
			columns.add(column);
		}
		if (columns.isEmpty()) {
			throw new IllegalArgumentException(
					"No writable Vertica COPY columns: " + table.getName());
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
				current = toCsv(rows.next()).getBytes(StandardCharsets.UTF_8);
				position = 0;
				rowCount++;
			}
			final int count = Math.min(length - written,
					current.length - position);
			System.arraycopy(current, position, buffer, offset + written, count);
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
				final String text = value instanceof byte[] bytes
						? java.util.HexFormat.of().formatHex(bytes)
						: value.toString();
				builder.append('"').append(text.replace("\"", "\"\""))
						.append('"');
			}
		}
		return builder.append('\n').toString();
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
				throw new IOException("Failed to close Vertica COPY rows", e);
			}
		}
	}
}
