/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.i;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.db2.i.Db2I75Test.*;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class Db2I75ColumnTypeTest {
	private static final boolean ZOS = false;

	@Test
	void nativeDistinctDefaultUsesTheTypeCastRatherThanABaseLiteral() {
		var table = read(new AtomicInteger(), "native-literal");
		String text = create(table);
		assertTrue(text.contains(ZOS ? "DEFAULT \"Type\"\"schema\".\"Money\"\"type\"(1)"
				: "DEFAULT CAST(1 AS \"Type\"\"schema\".\"Money\"\"type\")"), text);
	}

	@Test
	void viewColumnsRetainNamedTypesWithoutAdditionalQueries() throws Exception {
		var calls = new AtomicInteger();
		var views = new Db2I75().getCatalogReader().getSchemaReader().getViewReader()
				.getAllFull(connection(calls, "view"));
		assertEquals(2, calls.get());
		View view = SchemaUtils.readXml(new StringReader(views.get(0).asXml()));
		assertEquals("Money\"type", view.getColumns().get(1).getDomainName());
		assertTrue(new Db2I75().createSqlFactoryRegistry().createSql(view, SqlType.CREATE).stream()
				.anyMatch(op -> op.getSqlText().contains("CREATE VIEW")));
	}

	@Test
	void readsDistinctColumnsInSixBulkQueriesAndRecreatesAfterXml() throws Exception {
		var calls = new AtomicInteger();
		var table = read(calls, "normal");
		assertEquals(6, calls.get(), "No query per UDT, column or table");
		assertEquals(3, table.getColumns().size());
		Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		assertEquals("Type\"schema", restored.getColumns().get(1).getDomainSchemaName());
		assertEquals("Money\"type", restored.getColumns().get(1).getDomainName());
		assertNull(restored.getColumns().get(1).getLength());
		String text = create(restored);
		assertTrue(text.contains("\"A\" INTEGER"), text);
		assertTrue(text.contains("\"B\" \"Type\"\"schema\".\"Money\"\"type\""), text);
		assertFalse(text.contains("DISTINCT("), text);
		assertTrue(text.contains("DEFAULT CAST(1 AS \"Type\"\"schema\".\"Money\"\"type\")"), text);
		assertTrue(text.contains("NOT NULL"), text);
	}

	@Test
	void weakTypeIdentityIsRetainedEvenWhenCatalogReportsTheBaseType() {
		var table = read(new AtomicInteger(), "weak");
		var column = table.getColumns().get(1);
		assertEquals(DataType.OTHER, column.getDataType());
		assertEquals("Money\"type", column.getDomainName());
		assertTrue(create(table).contains("\"Type\"\"schema\".\"Money\"\"type\""));
	}

	@Test
	void domainReferencesFollowCanonicalRenameAndSurviveClone() {
		var table = read(new AtomicInteger(), "normal");
		var schema = new Schema("App");
		schema.getTables().add(table);
		var catalog = new Catalog();
		catalog.getSchemas().add(schema);
		var types = new Schema("Type\"schema");
		catalog.getSchemas().add(types);
		var money = new Domain("Money\"type");
		types.getDomains().add(money);
		assertSame(money, table.getColumns().get(1).getDomain());
		assertSame(money, table.getColumns().get(2).getDomain());
		// A native default expression is opaque SQL and is not rewritten on rename.
		table.getColumns().get(1).setDefaultValue(null);
		table.getColumns().get(2).setDefaultValue(null);
		money.setName("Renamed");
		assertTrue(create(table).contains("\"Type\"\"schema\".\"Renamed\""));
		assertEquals("Renamed", table.clone().getColumns().get(1).getDomainName());
	}

	@Test
	void missingNamedTypeIdentityFailsInsteadOfFallingBackToBaseType() {
		assertThrows(RuntimeException.class, () -> read(new AtomicInteger(), "missing-schema"));
		assertThrows(RuntimeException.class, () -> read(new AtomicInteger(), "missing-name"));
		assertThrows(RuntimeException.class, () -> read(new AtomicInteger(), "missing-both"));
	}

	@Test
	void unsupportedColumnOptionsCannotBeSilentlyAppendedToNamedTypes() {
		var table = read(new AtomicInteger(), "normal");
		var column = table.getColumns().get(1);
		column.setLength(5L);
		assertThrows(IllegalArgumentException.class, () -> create(table));
		column.setLength((Long) null);
		column.setIdentity(true);
		assertThrows(IllegalArgumentException.class, () -> create(table));
		column.setIdentity(false);
		column.getDomain().setArrayDimension(1);
		assertThrows(IllegalArgumentException.class, () -> create(table));
	}

	@Test
	void rawDomainSqlAndUnqualifiedNamesRequireExplicitModelIdentity() {
		var table = read(new AtomicInteger(), "normal");
		var column = table.getColumns().get(1);
		column.setDomainSchemaName(null);
		assertThrows(IllegalArgumentException.class, () -> create(table));
		column.setDomain(null);
		column.setDataType(DataType.DOMAIN).setDataTypeName("SomeType");
		assertThrows(IllegalArgumentException.class, () -> create(table));
	}

	@Test
	void ambiguousImplicitDistinctDefaultsFailExplicitly() {
		assertThrows(RuntimeException.class, () -> read(new AtomicInteger(), "implicit-default"));
	}

	private static Table read(AtomicInteger calls, String mode) {
		var reader = new Db2I75().getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName("App");
		return reader.getAllFull(connection(calls, mode)).get(0);
	}

	private static String create(Table table) {
		return new Db2I75().createSqlFactoryRegistry().createSql(table, SqlType.CREATE).stream()
				.map(op -> op.getSqlText()).reduce("", (a, b) -> a + "\n" + b);
	}

	private static Connection connection(AtomicInteger calls, String mode) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> metadata("DB2", ZOS ? "DSN13015" : "QSQ07050");
		case "prepareStatement" -> {
			String query = (String) a[0];
			assertFalse(query.contains("host:domains"), "Column metadata already carries the type identity");
			yield proxy(PreparedStatement.class, (sp, sm, sa) -> {
				if (!sm.getName().equals("executeQuery"))
					return fallback(sm.getReturnType());
				calls.incrementAndGet();
				if (query.contains("host:views"))
					return rows(List.of(row("SCHEMA_NAME", "App", "TABLE_NAME", "V", "VIEW_TEXT",
							ZOS ? "CREATE VIEW App.V AS SELECT A,B,C FROM App.T" : "SELECT A,B,C FROM App.T",
							"TEXT_KIND", ZOS ? "D" : "S", "CHECK_OPTION", "N", "SQL_PATH", null, "REMARKS", null)));
				if (query.contains("host:indexes") || query.contains("host:checks") || query.contains("KEY_TYPE")
						|| query.contains("REF_COLUMN_NAME"))
					return rows(List.of());
				if (query.contains("TYPE_NAME")) {
					assertTrue(query.contains(ZOS ? "c.TYPESCHEMA" : "c.USER_DEFINED_TYPE_SCHEMA"), query);
					if (ZOS)
						assertTrue(query.contains("DISTINCT IMPLICIT DEFAULT"), query);
					var base = row("SCHEMA_NAME", "App", "TABLE_NAME", mode.equals("view") ? "V" : "T", "COLUMN_NAME",
							"A", "TYPE_NAME", "INTEGER", "TYPE_LENGTH", 10L, "TYPE_SCALE", 0, "UDT_SCHEMA", null,
							"UDT_NAME", null, "NULLABLE", "Y", "REMARKS", null, "DEFAULT_SQL", null, "IDENTITY_MODE",
							"", "UNSUPPORTED_GENERATION", "");
					var named = new java.util.LinkedHashMap<>(base);
					named.put("COLUMN_NAME", "B");
					named.put("TYPE_NAME", mode.equals("weak") ? "DECIMAL" : "DISTINCT");
					named.put("UDT_SCHEMA",
							mode.equals("missing-schema") || mode.equals("missing-both") ? null : "Type\"schema");
					named.put("UDT_NAME",
							mode.equals("missing-name") || mode.equals("missing-both") ? null : "Money\"type");
					named.put("NULLABLE", "N");
					named.put("DEFAULT_SQL", "CAST(1 AS \"Type\"\"schema\".\"Money\"\"type\")");
					if (ZOS && mode.equals("native-literal")) {
						named.put("DEFAULT_SQL", "1");
						named.put("UDT_DEFAULT_CAST", "Y");
						base.put("UDT_DEFAULT_CAST", "N");
					}
					named.put("UNSUPPORTED_GENERATION",
							mode.equals("implicit-default") ? "DISTINCT IMPLICIT DEFAULT" : "");
					var third = new java.util.LinkedHashMap<>(named);
					third.put("COLUMN_NAME", "C");
					return rows(List.of(base, named, third));
				}
				return rows(List.of(row("SCHEMA_NAME", "App", "TABLE_NAME", "T", "REMARKS", null)));
			});
		}
		default -> fallback(m.getReturnType());
		});
	}
}
