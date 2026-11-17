/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import com.sqlapp.data.converter.IntervalValueConverter;
import com.sqlapp.data.db.datatype.IntervalArrays;
import com.sqlapp.data.db.datatype.MetaType;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

/** Lossless sqlapp interval text for CSV, TSV and JSONL; no destination normalization. */
final class TransferIntervalCodec {
	private static final tools.jackson.databind.ObjectMapper JSON = tools.jackson.databind.json.JsonMapper.builder(
			tools.jackson.core.json.JsonFactory.builder().streamReadConstraints(
					tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(34)
							.maxStringLength(1_048_576).maxNumberLength(64).build()).build())
			.disable(tools.jackson.databind.SerializationFeature.INDENT_OUTPUT)
			.enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
	private TransferIntervalCodec() {}

	static boolean interval(Column column) {
		return column.getDataType().getMetaTtype() == MetaType.INTERVAL;
	}

	static void validate(Column column) {
		if (!interval(column) || column.getArrayDimension() < 0 || column.getArrayDimension() > 32)
			throw new IllegalArgumentException("File interval dimensions must be 0..32: " + column.getName());
	}

	static String encode(Column column, Object value) {
		validate(column);
		if (value == null) return null;
		if (column.getArrayDimension() > 0) {
			Object array = value instanceof String text ? decode(column, text) : value;
			return JSON.writeValueAsString(IntervalArrays.map(array, dimensions(column, array),
					leaf -> encode(column.clone().setArrayDimension(0), leaf)));
		}
		IntervalValue interval = value instanceof String text ? (IntervalValue) decode(column, text)
				: new IntervalValueConverter().convertObject(value);
		if (interval == null) throw invalid(column, null);
		return interval.toString();
	}

	static Object decode(Column column, String text) {
		validate(column);
		if (text == null) return null;
		try {
			if (column.getArrayDimension() > 0) {
				var array = JSON.readValue(text, Object[].class);
				return IntervalArrays.map(array, dimensions(column, array), leaf -> {
					if (!(leaf instanceof String field)) throw invalid(column, null);
					return IntervalValue.parse(field);
				});
			}
			return IntervalValue.parse(text);
		} catch (RuntimeException failure) {
			throw invalid(column, failure);
		}
	}

	private static int dimensions(Column column, Object value) {
		var dialect = column.getDialect();
		if (dialect != null) {
			var type = dialect.getDbDataTypes().getDbTypeStrict(column.getDataType());
			if (type != null && type.getJdbcTypeHandler() instanceof com.sqlapp.data.db.datatype.IntervalTypeHandler handler
					&& !handler.fixedArrayDimensions()) return IntervalArrays.dimensions(value);
		}
		return column.getArrayDimension();
	}

	private static IllegalArgumentException invalid(Column column, Throwable cause) {
		return new IllegalArgumentException("Interval column '" + column.getName()
				+ "' requires sqlapp interval text (up to 9 fractional digits), or a JSON array "
				+ "of such strings matching its dimensions; "
				+ "database-specific and legacy interval text are not accepted.", cause);
	}
}
