/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.metadata.ReaderOptions;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;

class FirebirdCompilationStateTest {
	private ReaderOptions withoutSource() {
		ReaderOptions options = new ReaderOptions();
		options.setReadStatement(false);
		return options;
	}

	private ExResultSet row(Map<String, Object> values) {
		return new ExResultSet(null) {
			@Override
			public Object getObject(String key) {
				return values.get(key.toUpperCase(java.util.Locale.ROOT));
			}

			@Override
			public String getString(String key) {
				Object value = getObject(key);
				return value == null ? null : value.toString();
			}

			@Override
			public boolean getBoolean(String key) {
				Object value = getObject(key);
				return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() != 0;
			}

			@Override
			public int getInt(String key) {
				Object value = getObject(key);
				return value instanceof Number n ? n.intValue() : 0;
			}
		};
	}

	@Test
	void nullBlrIsUnknownWhileZeroAndOneRemainDistinct() throws Exception {
		Procedure procedure = new Procedure("p");
		FirebirdCompilationState.blr(row(Map.of()), procedure);
		assertFalse(procedure.getSpecifics().containsKey("firebird.validBlr"));
		FirebirdCompilationState.blr(row(Map.of("RDB$VALID_BLR", 0)), procedure);
		assertEquals("false", procedure.getSpecifics().get("firebird.validBlr"));
		FirebirdCompilationState.blr(row(Map.of("RDB$VALID_BLR", 1)), procedure);
		assertEquals("true", procedure.getSpecifics().get("firebird.validBlr"));
	}

	@Test
	void legacyReadersNeverAccessNewCatalogFields() throws Exception {
		ExResultSet row = new ExResultSet(null) {
			@Override
			public String getString(String key) {
				return null;
			}

			@Override
			public Object getObject(String key) {
				throw new AssertionError("Unexpected legacy catalog field " + key);
			}
		};
		var procedure = new FirebirdProcedureReader(DialectHolder.defaultDialect20);
		procedure.setReaderOptions(new ReaderOptions());
		procedure.createProcedure(row);
		new FirebirdTriggerReader(DialectHolder.defaultDialect20).readVersionProperties(row, new Trigger("legacy"));
	}

	@Test
	void schemaReadersSelectTheCompatibleVersionClasses() {
		assertFalse(DialectHolder.defaultDialect20.getCatalogReader().getSchemaReader()
				.getProcedureReader() instanceof Firebird25ProcedureReader);
		assertInstanceOf(Firebird25ProcedureReader.class,
				DialectHolder.defaultDialect25.getCatalogReader().getSchemaReader().getProcedureReader());
		assertInstanceOf(Firebird25TriggerReader.class,
				DialectHolder.defaultDialect25.getCatalogReader().getSchemaReader().getTriggerReader());
		assertInstanceOf(Firebird25ProcedureReader.class,
				DialectHolder.defaultDialect50.getCatalogReader().getSchemaReader().getProcedureReader());
	}

	@Test
	void procedureTriggerAndFunctionReadersKeepValidityWithoutSource() throws Exception {
		var procedure = new Firebird25ProcedureReader(DialectHolder.defaultDialect25);
		procedure.setReaderOptions(withoutSource());
		assertEquals("false",
				procedure.createProcedure(row(Map.of("RDB$VALID_BLR", 0))).getSpecifics().get("firebird.validBlr"));
		Trigger trigger = new Trigger("t");
		new Firebird30TriggerReader(DialectHolder.defaultDialect30)
				.readVersionProperties(row(Map.of("RDB$VALID_BLR", 1)), trigger);
		assertEquals("true", trigger.getSpecifics().get("firebird.validBlr"));
		var function = new Firebird30FunctionReader(DialectHolder.defaultDialect30);
		function.setReaderOptions(withoutSource());
		assertEquals("true",
				function.createFunction(row(Map.of("RDB$VALID_BLR", 1))).getSpecifics().get("firebird.validBlr"));
	}

	@Test
	void packageDistinguishesMissingInvalidAndValidBodies() throws Exception {
		var reader = new Firebird30PackageReader(DialectHolder.defaultDialect30);
		reader.setReaderOptions(withoutSource());
		var missing = reader.createPackage(row(Map.of("HAS_BODY", 0)));
		assertEquals("false", missing.getSpecifics().get("firebird.hasBody"));
		assertEquals("false", missing.getSpecifics().get("firebird.validBody"));
		var invalid = reader.createPackage(row(Map.of("HAS_BODY", 1, "RDB$VALID_BODY_FLAG", 0)));
		assertEquals("true", invalid.getSpecifics().get("firebird.hasBody"));
		assertEquals("false", invalid.getSpecifics().get("firebird.validBody"));
		var body = new Firebird30PackageBodyReader(DialectHolder.defaultDialect30);
		body.setReaderOptions(withoutSource());
		var valid = body.createPackageBody(row(Map.of("HAS_BODY", 1, "RDB$VALID_BODY_FLAG", 1)));
		assertEquals("true", valid.getSpecifics().get("firebird.validBody"));
		assertTrue(valid.getStatement().isEmpty());
	}
}
