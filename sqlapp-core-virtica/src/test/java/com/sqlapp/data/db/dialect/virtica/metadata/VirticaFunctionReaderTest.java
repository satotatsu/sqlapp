/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.virtica.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.NamedArgument;

class VirticaFunctionReaderTest {

	private final VirticaFunctionReader reader = new VirticaFunctionReader(DialectHolder.defaultDialect);

	@Test
	void parsesMultiCharacterArgumentName() throws SQLException {
		NamedArgument argument = reader.createNamedArgument("input_value Integer");

		assertEquals("input_value", argument.getName());
		assertEquals(DataType.BIGINT, argument.getDataType());
	}

	@Test
	void parsesUnnamedUdxArgumentType() throws SQLException {
		NamedArgument argument = reader.createNamedArgument("Numeric");

		assertNull(argument.getName());
		assertEquals(DataType.NUMERIC, argument.getDataType());
	}

	@Test
	void splitsArgumentsWithoutSplittingTypePrecision() {
		assertEquals(List.of("amount Numeric(10,2)", "label Varchar(100)"),
				reader.splitArguments("amount Numeric(10,2), label Varchar(100)"));
	}

	@Test
	void acceptsNoArguments() {
		assertEquals(List.of(), reader.splitArguments(null));
		assertEquals(List.of(), reader.splitArguments("  "));
		assertEquals("zero_args()", reader.createSpecificName("zero_args", null));
	}

	@Test
	void createsOverloadSpecificNameFromArguments() {
		assertEquals("calculate(amount Numeric(10,2), label Varchar(100))",
				reader.createSpecificName("calculate",
						"amount Numeric(10,2), label Varchar(100)"));
	}
}
