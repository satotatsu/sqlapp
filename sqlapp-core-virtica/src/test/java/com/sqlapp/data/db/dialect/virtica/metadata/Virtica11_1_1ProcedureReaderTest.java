/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.virtica.DialectHolder;
import com.sqlapp.jdbc.sql.ParameterDirection;

class Virtica11_1_1ProcedureReaderTest {

	private final Virtica11_1_1ProcedureReader reader =
			new Virtica11_1_1ProcedureReader(DialectHolder.defaultDialect11_1_1);

	@Test
	void parsesDefaultInputArgument() {
		var argument = reader.createArgument("input_value int");

		assertEquals("input_value", argument.getName());
		assertEquals(ParameterDirection.Input, argument.getDirection());
		assertEquals(DataType.BIGINT, argument.getDataType());
	}

	@Test
	void parsesExplicitDirection() {
		var argument = reader.createArgument("INOUT result_value varchar(100)");

		assertEquals("result_value", argument.getName());
		assertEquals(ParameterDirection.Inout, argument.getDirection());
		assertEquals(DataType.VARCHAR, argument.getDataType());
		assertEquals(100L, argument.getLength().longValue());
	}
}
