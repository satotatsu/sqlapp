package com.sqlapp.data.db.command.generator;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.command.generator.GeneratorPreviewDiagnostic.Code.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.Table;

class GeneratorPreviewValidatorTest {
	private GeneratorPreviewRow row(Map<String, Object> values) {
		return new GeneratorPreviewRow(null, "PUBLIC", "OUTPUT", null, 2, 3, values);
	}

	@Test
	void reportsExplicitNullButNotMissingDefaultIdentityOrComputedValues() {
		var table = new Table("OUTPUT");
		table.getColumns().add("REQUIRED", c -> c.setNotNull(true).setDefaultValue("1"));
		table.getColumns().add("ABSENT", c -> c.setNotNull(true).setDefaultValue("1"));
		table.getColumns().add("IDENTITY", c -> c.setNotNull(true).setIdentity(true));
		table.getColumns().add("COMPUTED", c -> c.setNotNull(true).setFormula("1 + 1"));
		table.getColumns().add("OPTIONAL");
		var values = new LinkedHashMap<String, Object>();
		for (String name : List.of("REQUIRED", "IDENTITY", "COMPUTED", "OPTIONAL"))
			values.put(name, null);
		var sample = row(values);
		var issues = GeneratorPreviewValidator.validate(table, sample);
		assertEquals(1, issues.size());
		assertEquals(NULL_FOR_NOT_NULL, issues.get(0).code());
		assertEquals("REQUIRED", issues.get(0).columnName());
		assertSame(sample, issues.get(0).row());
		assertThrows(UnsupportedOperationException.class, () -> issues.clear());
	}

	@Test
	void characterBoundariesUseCodePointsAndInheritedSemantics() {
		var table = new Table("OUTPUT");
		table.setCharacterSemantics(CharacterSemantics.Char);
		table.getColumns().add("TEXT", c -> c.setDataType(DataType.VARCHAR).setLength(2));
		String emoji = new String(Character.toChars(0x1F600));
		assertTrue(GeneratorPreviewValidator.validate(table, row(Map.of("TEXT", emoji + emoji))).isEmpty());
		var issues = GeneratorPreviewValidator.validate(table, row(Map.of("TEXT", "abc")));
		assertEquals(CHARACTER_LENGTH_EXCEEDED, issues.get(0).code());
		assertTrue(issues.get(0).message().contains("length=3"));
		assertTrue(issues.get(0).message().contains("length=2"));
	}

	@Test
	void doesNotGuessByteLengthsUnknownSemanticsOrConversions() {
		var table = new Table("OUTPUT");
		table.getColumns().add("BYTE_TEXT",
				c -> c.setDataType(DataType.VARCHAR).setLength(1).setCharacterSemantics(CharacterSemantics.Byte));
		table.getColumns().add("UNKNOWN_TEXT", c -> c.setDataType(DataType.VARCHAR).setLength(1));
		table.getColumns().add("ARRAY_TEXT", c -> c.setDataType(DataType.VARCHAR).setLength(1)
				.setCharacterSemantics(CharacterSemantics.Char).setArrayDimension(1));
		table.getColumns().add("CONVERTED",
				c -> c.setDataType(DataType.VARCHAR).setLength(1).setCharacterSemantics(CharacterSemantics.Char));
		assertTrue(GeneratorPreviewValidator
				.validate(table,
						row(Map.of("BYTE_TEXT", "abc", "UNKNOWN_TEXT", "abc", "ARRAY_TEXT", "abc", "CONVERTED", 12345)))
				.isEmpty());
	}

	@Test
	void exactDecimalsCheckIntegerAndFractionalBoundariesWithoutRounding() {
		var table = decimalTable(5, 2);
		for (String text : List.of("999.99", "-999.99", "999.9900", "0.0000", "0.01", "100.00")) {
			assertTrue(GeneratorPreviewValidator.validate(table, row(Map.of("N", new BigDecimal(text)))).isEmpty(),
					text);
		}
		for (Object value : List.of(new BigDecimal("1000"), new BigDecimal("-1000"), new BigDecimal("1E+3"),
				new BigInteger("1000"), 1000L)) {
			var issues = GeneratorPreviewValidator.validate(table, row(Map.of("N", value)));
			assertEquals(List.of(NUMERIC_INTEGER_DIGITS_EXCEEDED),
					issues.stream().map(GeneratorPreviewDiagnostic::code).toList());
		}
		var issues = GeneratorPreviewValidator.validate(table, row(Map.of("N", new BigDecimal("1000.001"))));
		assertEquals(List.of(NUMERIC_INTEGER_DIGITS_EXCEEDED, NUMERIC_SCALE_EXCEEDED),
				issues.stream().map(GeneratorPreviewDiagnostic::code).toList());
	}

	@Test
	void supportsFractionalPrecisionAndLargeScientificExponents() {
		var table = decimalTable(3, 5);
		for (String text : List.of("0", "0.001", "0.00999")) {
			assertTrue(GeneratorPreviewValidator.validate(table, row(Map.of("N", new BigDecimal(text)))).isEmpty());
		}
		assertEquals(NUMERIC_INTEGER_DIGITS_EXCEEDED,
				GeneratorPreviewValidator.validate(table, row(Map.of("N", new BigDecimal("0.01")))).get(0).code());
		assertEquals(NUMERIC_INTEGER_DIGITS_EXCEEDED, GeneratorPreviewValidator
				.validate(decimalTable(5, 2), row(Map.of("N", new BigDecimal(BigInteger.TEN, Integer.MIN_VALUE))))
				.get(0).code());
	}

	@Test
	void skipsApproximateNumbersStringsAndUnknownOrNegativeScale() {
		var table = decimalTable(5, 2);
		for (Object value : List.of(10000.123d, Float.NaN, "10000.123")) {
			assertTrue(GeneratorPreviewValidator.validate(table, row(Map.of("N", value))).isEmpty());
		}
		table.getColumns().get("N").setScale(null);
		assertTrue(GeneratorPreviewValidator.validate(table, row(Map.of("N", new BigDecimal("10000.123")))).isEmpty());
		table.getColumns().get("N").setScale(-2);
		assertTrue(GeneratorPreviewValidator.validate(table, row(Map.of("N", new BigDecimal("10000.123")))).isEmpty());
	}

	private Table decimalTable(long precision, int scale) {
		var table = new Table("OUTPUT");
		table.getColumns().add("N", c -> c.setDataType(DataType.DECIMAL).setLength(precision).setScale(scale));
		return table;
	}
}
