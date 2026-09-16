/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

/** One deterministic schema compatibility finding. */
public record SchemaCompatibilityChange(SchemaCompatibility compatibility, String objectId, String property,
		String expected, String actual, String message) {
}
