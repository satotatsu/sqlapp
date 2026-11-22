/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class TransferTargetContractTest {
	/**
	 * Declared type, target type, whether the target accepts every declared value.
	 */
	@ParameterizedTest
	@CsvSource({ "VARCHAR(10),VARCHAR(10),true", "VARCHAR(10),VARCHAR(9),false", "VARCHAR(10),CLOB,true",
			"VARCHAR(10),NVARCHAR(20),true", "VARCHAR(10),INT,false", "CHAR(3),VARCHAR(3),true", "INT,BIGINT,true",
			"BIGINT,INT,false", "INT,DECIMAL(10),true", "INT,DECIMAL(9),false", "INT,NUMERIC,true",
			"DECIMAL(9),INT,true", "DECIMAL(10),INT,false", "DECIMAL(9;1),INT,false",
			"DECIMAL(12;2),DECIMAL(13;3),true", "DECIMAL(12;2),DECIMAL(12;3),false", "DECIMAL(12;2),DOUBLE,false",
			"REAL,DOUBLE,true", "DOUBLE,REAL,false", "DOUBLE,DECIMAL(30;10),false", "DATE,TIMESTAMP,true",
			"TIMESTAMP,DATE,false", "TIMESTAMP(;6),TIMESTAMP(;3),false", "TIMESTAMP(;3),TIMESTAMP(;6),true",
			"TIME,TIMESTAMP,false", "BOOLEAN,BOOLEAN,true", "BOOLEAN,INT,false", "VARBINARY(8),BLOB,true",
			"VARBINARY(8),VARBINARY(4),false", "UUID,VARCHAR(36),false" })
	void acceptsTypesThatHoldEveryDeclaredValue(final String declared, final String actual, final boolean accepted) {
		final List<String> problems = TransferTargetContract.violations(table(column("C", declared)),
				table(column("C", actual)));
		assertEquals(accepted, problems.isEmpty(), declared + " -> " + actual + ": " + problems);
	}

	@ParameterizedTest
	@CsvSource({ "false,false,true", "true,false,true", "false,true,false", "true,true,true" })
	void nullableDeclaredColumnsNeedNullableTargets(final boolean declaredNotNull, final boolean targetNotNull,
			final boolean accepted) {
		final List<String> problems = TransferTargetContract.violations(
				table(column("C", "INT").setNotNull(declaredNotNull)),
				table(column("C", "INT").setNotNull(targetNotNull)));
		assertEquals(accepted, problems.isEmpty(), problems.toString());
	}

	@ParameterizedTest
	@CsvSource({ "ID,ID,true", "ID,ID NAME,true", "ID,NAME,false", "ID NAME,ID,false", ",ID,false", ",,true" })
	void targetUniqueKeysMustContainTheDeclaredKey(final String declaredKey, final String targetKey,
			final boolean accepted) {
		final Table declared = table(column("ID", "INT").setNotNull(true),
				column("NAME", "VARCHAR(10)").setNotNull(true));
		final Table actual = table(column("ID", "INT").setNotNull(true),
				column("NAME", "VARCHAR(10)").setNotNull(true));
		key(declared, declaredKey);
		key(actual, targetKey);
		final List<String> problems = TransferTargetContract.violations(declared, actual);
		assertEquals(accepted, problems.isEmpty(), problems.toString());
	}

	@ParameterizedTest
	@CsvSource({ "false,,true", "true,,false", "true,0,true" })
	void extraTargetColumnsMustAcceptMissingValues(final boolean notNull, final String defaultValue,
			final boolean accepted) {
		final Table actual = table(column("C", "INT"), column("EXTRA", "INT").setNotNull(notNull));
		actual.getColumns().get("EXTRA").setDefaultValue(defaultValue);
		final List<String> problems = TransferTargetContract.violations(table(column("C", "INT")), actual);
		assertEquals(accepted, problems.isEmpty(), problems.toString());
		if (!accepted) {
			assertTrue(problems.getFirst().startsWith("EXTRA: "), problems.toString());
		}
	}

	private static void key(final Table table, final String columns) {
		if (columns != null) {
			table.setPrimaryKey("PK_" + table.getName(), java.util.Arrays.stream(columns.split(" "))
					.map(name -> table.getColumns().get(name)).toArray(Column[]::new));
		}
	}

	private static Table table(final Column... columns) {
		final Table table = new Table("T");
		for (final Column column : columns) {
			table.getColumns().add(column);
		}
		return table;
	}

	/** {@code NAME}, {@code NAME(length)} or {@code NAME(length;scale)}. */
	private static Column column(final String name, final String type) {
		final var matcher = java.util.regex.Pattern.compile("(\\w+)(?:\\((\\d*)(?:;(\\d+))?\\))?").matcher(type);
		assertTrue(matcher.matches(), type);
		final Column column = new Column(name).setDataType(DataType.valueOf(matcher.group(1)));
		if (matcher.group(2) != null && !matcher.group(2).isEmpty()) {
			column.setLength(Long.valueOf(matcher.group(2)));
		}
		if (matcher.group(3) != null) {
			column.setScale(Integer.valueOf(matcher.group(3)));
		}
		return column;
	}
}
