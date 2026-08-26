/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.io.Serializable;

import com.sqlapp.data.schemas.Row;

/** Selects the row retained when two bulk-upsert source rows have one key. */
@FunctionalInterface
public interface BulkUpsertDuplicateRowSelector extends Serializable {
	/** Returns the row to retain; returning {@code null} is invalid. */
	Row select(Row retained, Row candidate);
}
