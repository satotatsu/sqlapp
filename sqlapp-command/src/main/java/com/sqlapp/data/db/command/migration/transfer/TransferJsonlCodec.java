/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import tools.jackson.databind.DeserializationFeature;

/**
 * Flat, bounded JSON objects; column types remain owned by the captured Schema.
 */
final class TransferJsonlCodec {
	private static final int MAX_FIELD = 1_048_576;
	private static final int MAX_LINE = 16_777_216;
	private static final tools.jackson.databind.ObjectMapper MAPPER = tools.jackson.databind.json.JsonMapper
			.builder(tools.jackson.core.json.JsonFactory.builder()
					.streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxStringLength(MAX_FIELD)
							.maxNumberLength(MAX_FIELD).maxNestingDepth(2).build())
					.build())
			.disable(tools.jackson.databind.SerializationFeature.INDENT_OUTPUT)
			.enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
			.enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();

	static String write(Table table, String[] values) {
		var object = new LinkedHashMap<String, Object>();
		long size = 0;
		for (int index = 0; index < values.length; index++) {
			var column = table.getColumns().get(index);
			var value = values[index];
			size += column.getName().length() + (value == null ? 4 : value.length());
			if (size > MAX_LINE)
				throw new IllegalArgumentException("JSONL row exceeds the 16 MiB character limit.");
			object.put(column.getName(), value == null ? null
					: numeric(column) ? new BigDecimal(value) : bool(column) ? Boolean.valueOf(value) : value);
		}
		var line = MAPPER.writeValueAsString(object);
		if (line.length() > MAX_LINE)
			throw new IllegalArgumentException("JSONL row exceeds the 16 MiB character limit.");
		return line;
	}

	static String[] read(Reader reader, Table table) throws IOException {
		var line = new StringBuilder();
		int character;
		while ((character = reader.read()) != -1 && character != '\n') {
			if (line.length() >= MAX_LINE)
				throw new IllegalArgumentException("JSONL row exceeds the 16 MiB character limit.");
			line.append((char) character);
		}
		if (character == -1 && line.isEmpty())
			return null;
		var object = MAPPER.readValue(line.toString(), Map.class);
		if (object == null || object.size() != table.getColumns().size() || !object.keySet()
				.equals(new java.util.HashSet<>(table.getColumns().stream().map(Column::getName).toList())))
			throw new IllegalArgumentException("JSONL fields differ from canonical Schema.");
		var values = new String[table.getColumns().size()];
		for (int index = 0; index < values.length; index++) {
			var column = table.getColumns().get(index);
			var value = object.get(column.getName());
			if (value == null)
				continue;
			if (numeric(column) ? !(value instanceof Number)
					: bool(column) ? !(value instanceof Boolean) : !(value instanceof String))
				throw new IllegalArgumentException("JSONL scalar type differs from canonical Schema.");
			if (value instanceof BigDecimal decimal
					&& (Math.abs((long) decimal.scale()) + decimal.precision() > MAX_FIELD))
				throw new IllegalArgumentException("JSONL number exceeds the 1 MiB character limit.");
			values[index] = value instanceof BigDecimal decimal ? decimal.toPlainString() : value.toString();
			if (values[index].length() > MAX_FIELD)
				throw new IllegalArgumentException("JSONL field exceeds the 1 MiB character limit.");
			if (!java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(values[index]))
				throw new IllegalArgumentException("JSONL field contains invalid Unicode.");
		}
		return values;
	}

	private static boolean bool(Column column) {
		return column.getDataType().getJdbcType() == java.sql.JDBCType.BOOLEAN
				|| column.getDataType().getJdbcType() == java.sql.JDBCType.BIT;
	}

	private static boolean numeric(Column column) {
		return switch (column.getDataType().getJdbcType()) {
		case TINYINT, SMALLINT, INTEGER, BIGINT, NUMERIC, DECIMAL, REAL, FLOAT, DOUBLE -> true;
		default -> false;
		};
	}
}
