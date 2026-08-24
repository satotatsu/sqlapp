/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Function;

class InformixRoutineUtilsTest {
	@Test
	void splitsArgumentsOutsideTypeAndQuotedDefaultCommas() {
		Function function = new Function("metadata_function");

		InformixRoutineUtils.setArguments(function, """
				CREATE FUNCTION metadata_function(
					p_amount DECIMAL(10, 2),
					p_label VARCHAR(30) DEFAULT 'x,y',
					p_quote VARCHAR(30) DEFAULT 'it''s,quoted')
				RETURNING INTEGER;
				RETURN 1;
				END FUNCTION
				""");

		assertEquals(3, function.getArguments().size());
		assertEquals("p_amount", function.getArguments().get(0).getName());
		assertEquals("p_label", function.getArguments().get(1).getName());
		assertEquals("p_quote", function.getArguments().get(2).getName());
	}
}
