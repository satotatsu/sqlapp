package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RoutineSpecificNamePreservationTest {
	@Test void preservesSignatureTypeQualifiersAndQuotedRoutineNames() throws Exception {
		Function function = new Function("f").setSchemaName("original");
		String signature = "f(other.\"State.Type\"[])";
		function.setSpecificName(signature);
		assertEquals("original", function.getSchemaName());
		assertEquals(signature, function.getSpecificName());
		Function restored = SchemaUtils.readXml(new java.io.StringReader(function.asXml()));
		assertEquals("original", restored.getSchemaName()); assertEquals(signature, restored.getSpecificName());
		Procedure procedure = new Procedure("p").setSchemaName("original").setSpecificName("\"p.name\"(other.value)");
		assertEquals("original", procedure.getSchemaName());
		assertEquals("\"p.name\"(other.value)", procedure.getSpecificName());
	}
	@Test void retainsQualifiedNameAndNullBehavior() {
		Function dotted = new Function("f.name").setSchemaName("original").setSpecificName("f.name(other.value)");
		assertEquals("original", dotted.getSchemaName()); assertEquals("f.name(other.value)", dotted.getSpecificName());
		Function function = new Function("f").setSpecificName("schema.f(other.value)");
		assertEquals("schema", function.getSchemaName()); assertEquals("f(other.value)", function.getSpecificName());
		function.setSpecificName(null); assertEquals("f", function.getSpecificName());
	}
}
