/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValuesXmlRoundTripTest {
	@Test
	void explicitXmlValuesPreserveCommasQuotesWhitespaceAndEmptyStrings() throws Exception {
		List<String> values = List.of("'a, b'", "'O''Reilly'", "plain,comma", "  spaced  ", "", "日本語");
		NamedArgument argument = new NamedArgument("choice");
		argument.getValues().addAll(values);
		Procedure procedure = new Procedure("choice_routine");
		procedure.getArguments().add(argument);
		Procedure restored = SchemaUtils.readXml(new StringReader(procedure.asXml()));
		assertEquals(values, List.copyOf(restored.getArguments().get(0).getValues()));
		Column column = new Column("choice");
		column.getValues().addAll(values);
		Column restoredColumn = SchemaUtils.readXml(new StringReader(column.asXml()));
		assertEquals(values, List.copyOf(restoredColumn.getValues()));
	}
	@Test
	void legacyCommaSeparatedPropertyInputRemainsSupported() {
		Column column = new Column("choice");
		SchemaProperties.VALUES.setValue(column, "one, two,three");
		assertEquals(List.of("one", "two", "three"), List.copyOf(column.getValues()));
	}
}
