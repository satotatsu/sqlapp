/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;

/** Converts a complete ordered key between row values and a durable token. */
public interface BulkMigrationKeysetCodec {
	String encode(List<Column> keyColumns, Row row);

	List<Object> decode(List<Column> keyColumns, String token);
}
