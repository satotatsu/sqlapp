/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.vertica.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.vertica.DialectHolder;
import com.sqlapp.data.db.dialect.vertica.metadata.Vertica11_1_1ProcedureReader;
import com.sqlapp.jdbc.sql.ParameterDirection;

class Vertica11_1_1ProcedureReaderTest {

	private final Vertica11_1_1ProcedureReader reader = new Vertica11_1_1ProcedureReader(
			DialectHolder.defaultDialect11_1_1);

	@Test
	void parsesDefaultInputArgument() {
		var argument = reader.createArgument("input_value int");

		assertEquals("input_value", argument.getName());
		assertEquals(ParameterDirection.Input, argument.getDirection());
		assertEquals(DataType.BIGINT, argument.getDataType());
	}

	@Test
	void parsesQuotedNameAndRejectsInvalidArgument() {
		var argument = reader.createArgument("INOUT \"order value\" numeric(12,2)");
		assertEquals("order value", argument.getName());
		String sql = DialectHolder.defaultDialect11_1_1.createSqlBuilder().argument(argument).toString();
		org.junit.jupiter.api.Assertions.assertTrue(sql.contains("\"order value\""), sql);
		assertEquals(sql.indexOf("INOUT"), sql.lastIndexOf("INOUT"));
		assertThrows(IllegalArgumentException.class, () -> reader.createArgument("missing_type"));
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
