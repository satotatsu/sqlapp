/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.bulk;

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

/** Streams Schema rows as UTF-8 delimited data for Vertica COPY FROM STDIN. */
public class VerticaBulkDataInputStream extends InputStream {
	static final char NULL_MARKER = '\u001d';
	static final char RECORD_TERMINATOR = '\u001e';
	static final char DELIMITER = '\u001f';
	private final List<Column> columns = new ArrayList<>();
	private final Iterator<Row> rows;
	private byte[] current = new byte[0];
	private int position;
	private long rowCount;
	private boolean closed;
	private String inputPrefix="__sqlapp_copy_";

	public VerticaBulkDataInputStream(final Table table, final BulkOption options) {
		java.util.Objects.requireNonNull(table, "table");
		final BulkOption effective = options == null ? BulkOption.defaults() : options;
		for (final Column column : table.getColumns()) {
			if (!writable(column,effective)) {
				continue;
			}
			columns.add(column);
		}
		if (columns.isEmpty()) {
			throw new IllegalArgumentException("No writable Vertica COPY columns: " + table.getName());
		}
		while(hasInputPrefix(inputPrefix))inputPrefix+="_";
		rows = table.getRows().iterator();
	}

	private boolean hasInputPrefix(String prefix) {
		return columns.stream().anyMatch(column->column.getName().toLowerCase(java.util.Locale.ROOT).startsWith(prefix));
	}
	static boolean writable(Column column,BulkOption options) {
		return !column.isHidden() && CommonUtils.isEmpty(column.getFormula())
				&& (!column.isIdentity() || options != null && options.isKeepIdentity());
	}
	static boolean binary(Column column) { return column.getDataType()!=null && column.getDataType().isBinary(); }
	static boolean transformedInterval(Column column) {
		return column.getDataType()!=null && column.getDataType().isInterval()
				&& (column.getArrayDimension()==0 );
	}
	static String binaryText(byte[] bytes) {
		var text=new StringBuilder(Math.multiplyExact(bytes.length,4));
		for(byte value:bytes){int b=value&255;text.append('\\').append((char)('0'+(b>>>6))).append((char)('0'+((b>>>3)&7))).append((char)('0'+(b&7)));}
		return text.toString();
	}

	String binaryInputName(Column column,int part){return inputPrefix+column.getOrdinal()+"_"+part;}
	String inputName(Column column) {
		return transformedInterval(column) ? inputPrefix+column.getOrdinal() : column.getName();
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
	public int read(final byte[] buffer, final int offset, final int length) throws IOException {
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
				current = encodeRow(rows.next()).getBytes(StandardCharsets.UTF_8);
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

	protected String encodeRow(final Row row) {
		final StringBuilder builder = new StringBuilder();
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) {
				builder.append(DELIMITER);
			}
			final Object value = row.get(columns.get(i).getOrdinal());
			if (value == null) {
				builder.append(NULL_MARKER);
			} else {
				final var column = columns.get(i);
				final String text = column.getDataType() != null && column.getDataType().isInterval()
						? new com.sqlapp.data.db.dialect.vertica.VerticaIntervalJdbcTypeHandler(column.getDataType()).text(value,column)
						: value instanceof byte[] bytes ? binaryText(bytes)
						: value.toString();
				appendEscaped(builder, text);
			}
		}
		return builder.append(RECORD_TERMINATOR).toString();
	}

	private void appendEscaped(final StringBuilder builder, final String text) {
		for (int i = 0; i < text.length(); i++) {
			final char character = text.charAt(i);
			if (character == '\\' || character == DELIMITER || character == RECORD_TERMINATOR
					|| character == NULL_MARKER) {
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
				throw new IOException("Failed to close Vertica COPY rows", e);
			}
		}
	}
}
