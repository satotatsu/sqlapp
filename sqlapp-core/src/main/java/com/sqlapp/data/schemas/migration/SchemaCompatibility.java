/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

/** Compatibility of an actual schema with the schema expected by a migration. */
public enum SchemaCompatibility {
	COMPATIBLE,
	CONDITIONAL,
	BREAKING;

	public SchemaCompatibility merge(final SchemaCompatibility value) {
		return ordinal() >= value.ordinal() ? this : value;
	}
}
