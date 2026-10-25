/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2RowTypeReaderTest {
	@Test
	void twentyTypesAndTheirFieldsUseOneQueryEachWithNativeUnitsAndModuleIsolation() {
		AtomicInteger typeQueries = new AtomicInteger(), fieldQueries = new AtomicInteger();
		var types = new Db2TypeReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode node, ParametersContext p,
					ResultSetNextHandler h) {
				typeQueries.incrementAndGet();
				assertTrue(node.toString().contains("TYPEMODULEID IS NULL"));
				for (int i = 0; i < 20; i++)
					try {
						h.handleResultSetNext(Db2MetadataParityTest
								.row(Map.of("type_name", "R" + i, "schema_name", "APP", "METATYPE", "F")));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				return null;
			}
		}.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		var fields = new Db2TypeColumnReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode node, ParametersContext p,
					ResultSetNextHandler h) {
				fieldQueries.incrementAndGet();
				assertTrue(node.toString().contains("a.TYPEMODULENAME IS NULL"));
				assertTrue(node.toString().contains("a.FIELDTYPESCHEMA"));
				for (int i = 0; i < 20; i++)
					for (int j = 0; j < 2; j++)
						try {
							var values = new HashMap<String, String>(Map.of("type_name", "R" + i, "schema_name", "APP",
									"column_name", "F" + j, "data_type", "VARCHAR", "data_type_schema", "SYSIBM",
									"LENGTH", "20", "SCALE", "0", "CODEPAGE", "1208", "TYPESTRINGUNITS", "CODEUNITS32",
									"STRINGUNITSLENGTH", "5"));
							values.put("ORDINAL", Integer.toString(j));
							h.handleResultSetNext(Db2MetadataParityTest.row(values));
						} catch (SQLException e) {
							throw new IllegalStateException(e);
						}
				return null;
			}
		}.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, typeQueries.get());
		assertEquals(1, fieldQueries.get());
		assertEquals(20, types.size());
		assertEquals(40, fields.size());
		assertEquals(Type.MetaType.Row, types.getFirst().getMetaType());
		assertEquals("F", types.getFirst().getSpecifics().get("METATYPE"));
		assertEquals("SYSIBM", fields.getFirst().getSpecifics().get("DB2_TYPE_SCHEMA"));
		assertEquals("5", fields.getFirst().getSpecifics().get("STRINGUNITSLENGTH"));
		assertEquals("0", fields.getFirst().getSpecifics().get("ORDINAL"));
		assertEquals("1", fields.get(1).getSpecifics().get("ORDINAL"));
	}

	@Test
	void fieldsRetainUserTypeIdentityAndLegacyResourceRemainsSeparate() throws Exception {
		var modern = new Db2TypeColumnReader(DialectHolder.Db2_1110Dialect);
		var field = modern.createTypeColumn(Db2MetadataParityTest.row(Map.of("type_name", "R", "schema_name", "APP",
				"column_name", "FIELD", "data_type", "NESTED", "data_type_schema", "OTHER", "data_type_module", "M")));
		assertEquals("OTHER", field.getSpecifics().get("DB2_TYPE_SCHEMA"));
		assertEquals("M", field.getSpecifics().get("DB2_TYPE_MODULE"));
		assertFalse(new Db2TypeColumnReader(DialectHolder.Db2_1050Dialect).getSqlNode(new ProductVersionInfo())
				.toString().contains("a.TYPEMODULENAME IS NULL"));
	}
}
