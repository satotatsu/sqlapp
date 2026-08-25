/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.loader;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SchemaFileLoaderResolverTest {

	@Test
	void rejectsUnsupportedFileClearly() {
		final var exception = assertThrows(IllegalArgumentException.class,
				() -> SchemaFileLoaderResolver.resolve(
						Path.of("unsupported.schema-file")));
		assertTrue(exception.getMessage().contains("No schema file loader"),
				exception.getMessage());
	}
}
