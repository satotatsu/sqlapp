/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.util.JsonUtils;

/** JSON token codec for standard Schema column converters. */
public class DefaultBulkMigrationKeysetCodec implements BulkMigrationKeysetCodec {
	@Override
	@SuppressWarnings("unchecked")
	public String encode(final List<Column> keyColumns, final Row row) {
		final List<String> values = new ArrayList<>(keyColumns.size());
		for (final Column column : keyColumns) {
			final Object value = row.get(column);
			if (value == null) {
				throw new IllegalArgumentException("Keyset column must not be null: " + column.getName());
			}
			values.add(column.getFormatter().format(value));
		}
		return JsonUtils.toJsonString(values);
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<Object> decode(final List<Column> keyColumns, final String token) {
		final List<?> encoded = JsonUtils.fromJsonString(token, List.class);
		if (encoded.size() != keyColumns.size()) {
			throw new IllegalArgumentException("Keyset token contains " + encoded.size()
					+ " values but " + keyColumns.size() + " key columns are configured");
		}
		final List<Object> values = new ArrayList<>(keyColumns.size());
		for (int i = 0; i < keyColumns.size(); i++) {
			final Object value = encoded.get(i);
			if (value == null) {
				throw new IllegalArgumentException("Keyset token contains a null value at index " + i);
			}
			values.add(keyColumns.get(i).getConverter().convertObject(value));
		}
		return values;
	}
}
