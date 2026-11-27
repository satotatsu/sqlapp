/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.db.dialect.db2.Db2ModuleMembers.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.schemas.Package;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2CursorTypeTest {
	private Type cursor(String name, String kind) {
		Type cursor = new Type(name).setSchemaName("APP").setMetaType(Type.MetaType.Cursor);
		cursor.getSpecifics().putAll(Map.of("METATYPE", "C", "DB2_CURSOR_KIND", kind));
		return cursor;
	}

	private List<String> sql(Type type) {
		SqlFactory<Type> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(type,
				SqlType.CREATE);
		return factory.createSql(type).stream().map(SqlOperation::getSqlText).toList();
	}

	@Test
	void weakAndNamedStrongCursorsSurviveXmlAndSchemaCreatesRowFirst() throws Exception {
		Type weak = cursor("W\".Q", "W"), strong = cursor("S", "S");
		strong.getSpecifics().putAll(Map.of("DB2_CURSOR_ROW_SCHEMA", "APP", "DB2_CURSOR_ROW_NAME", "R\".Q"));
		Type restored = SchemaUtils.readXml(new StringReader(strong.asXml()));
		assertEquals(strong, restored);
		assertEquals(strong, strong.clone());
		assertEquals(List.of("CREATE TYPE \"APP\".\"W\"\".Q\" AS CURSOR"), sql(weak));
		assertEquals(List.of("CREATE TYPE \"APP\".\"S\" AS \"APP\".\"R\"\".Q\" CURSOR"), sql(restored));
		Schema schema = new Schema("APP");
		schema.getTypes().add(strong);
		var row = new Type("R\".Q").setMetaType(Type.MetaType.Row);
		row.getColumns().add("ID", column -> column.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		schema.getTypes().add(row);
		SqlFactory<Schema> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(schema,
				SqlType.CREATE);
		var generated = factory.createSql(schema).stream().map(SqlOperation::getSqlText).toList();
		assertTrue(generated.indexOf(sql(row).getFirst()) < generated.indexOf(sql(strong).getFirst()));
	}

	@Test
	void ambiguousTargetsConflictingWeakFieldsAndOldDialectsRequireFullDdl() {
		for (Type type : List.of(cursor("U", "U"), cursor("S", "S")))
			assertThrows(IllegalArgumentException.class, () -> sql(type));
		var weak = cursor("W", "W");
		weak.getSpecifics().put("DB2_CURSOR_ROW_NAME", "R");
		assertThrows(IllegalArgumentException.class, () -> sql(weak));
		var source = cursor("W", "W");
		SqlFactory<Type> old = DialectHolder.Db2_1050Dialect.createSqlFactoryRegistry().getSqlFactory(source,
				SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> old.createSql(source));
		source.setDefinition("CREATE TYPE APP.W AS CURSOR");
		assertEquals(source.getDefinition(), sql(source));
	}

	@Test
	void globalCursorRetainsQuotedModuleRowTargetAndSchemaCreatesModuleFirst() throws Exception {
		Package module = new Package("M\".Q").setSchemaName("APP");
		var row = new Type("R\".Q").setSchemaName("APP").setMetaType(Type.MetaType.Row);
		row.getColumns().add("ID", field -> field.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		Db2ModuleMembers.set(module, List.of(Db2ModuleMembers.withRowType(
				new Member(Kind.TYPE, row.getName(), null, true, true, Map.of("TYPE_METATYPE", "F", "TYPE_NULLS", "Y")),
				row)));
		var cursor = cursor("C", "S");
		cursor.getSpecifics().putAll(Map.of("DB2_CURSOR_ROW_SCHEMA", "APP", "DB2_CURSOR_ROW_MODULE", module.getName(),
				"DB2_CURSOR_ROW_NAME", row.getName()));
		String expected = "CREATE TYPE \"APP\".\"C\" AS \"APP\".\"M\"\".Q\".\"R\"\".Q\" CURSOR";
		assertEquals(expected, sql(SchemaUtils.<Type>readXml(new StringReader(cursor.asXml()))).getFirst());
		Schema schema = new Schema("APP");
		schema.getTypes().add(cursor);
		schema.getPackages().add(module);
		SqlFactory<Schema> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(schema,
				SqlType.CREATE);
		var statements = factory.createSql(schema).stream().map(SqlOperation::getSqlText).toList();
		int rowIndex = java.util.stream.IntStream.range(0, statements.size())
				.filter(index -> statements.get(index).contains("PUBLISH TYPE")).findFirst().orElseThrow();
		assertTrue(rowIndex < statements.indexOf(expected));
		cursor.getSpecifics().put("DB2_CURSOR_ROW_MODULE", "");
		assertThrows(IllegalArgumentException.class, () -> sql(cursor));
	}

	@Test
	void twentyCursorHeadersUseOneBulkQueryAndRetainNamedTargets() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2TypeReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				assertTrue(node.toString().contains("SYSCAT.DATATYPEDEP"));
				for (int i = 0; i < 20; i++)
					try {
						handler.handleResultSetNext(Db2MetadataParityTest
								.row(Map.of("type_name", "C" + i, "schema_name", "APP", "METATYPE", "C", "CURSOR_KIND",
										"S", "CURSOR_ROW_SCHEMA", "APP", "CURSOR_ROW_NAME", "R" + i)));
					} catch (SQLException exception) {
						throw new IllegalStateException(exception);
					}
				return null;
			}
		};
		var types = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, queries.get());
		assertEquals(20, types.size());
		assertEquals("CREATE TYPE \"APP\".\"C19\" AS \"APP\".\"R19\" CURSOR", sql(types.getLast()).getFirst());
	}

	private Member cursorMember(String name, String kind) {
		return new Member(Kind.TYPE, name, null, true, true,
				Map.of("TYPE_METATYPE", "C", "TYPE_NULLS", "Y", "TYPE_CURSOR_KIND", kind));
	}

	private List<String> sql(Package module) {
		SqlFactory<Package> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(module,
				SqlType.CREATE);
		return factory.createSql(module).stream().map(SqlOperation::getSqlText).toList();
	}

	@Test
	void moduleCursorsReferenceOnlyEarlierPublishedRowTypes() throws Exception {
		Package module = new Package("M").setSchemaName("APP");
		var row = new Type("R").setSchemaName("APP").setMetaType(Type.MetaType.Row);
		row.getColumns().add("ID", column -> column.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		var rowMember = Db2ModuleMembers.withRowType(
				new Member(Kind.TYPE, "R", null, true, true, Map.of("TYPE_METATYPE", "F", "TYPE_NULLS", "Y")), row);
		var fields = new HashMap<>(cursorMember("S", "S").attributes());
		fields.putAll(
				Map.of("TYPE_CURSOR_ROW_SCHEMA", "APP", "TYPE_CURSOR_ROW_MODULE", "M", "TYPE_CURSOR_ROW_NAME", "R"));
		var strong = new Member(Kind.TYPE, "S", null, true, true, fields);
		Db2ModuleMembers.set(module, List.of(rowMember, cursorMember("W", "W"), strong));
		Package restored = SchemaUtils.readXml(new StringReader(module.asXml()));
		assertTrue(sql(restored).getLast().endsWith("PUBLISH TYPE \"S\" AS \"R\" CURSOR"));
		Db2ModuleMembers.set(module, List.of(strong, rowMember));
		assertThrows(IllegalArgumentException.class, () -> sql(module));
		Db2ModuleMembers.set(module,
				List.of(new Member(Kind.TYPE, "R", null, false, true, rowMember.attributes()), strong));
		assertThrows(IllegalArgumentException.class, () -> sql(module));
		fields.put("TYPE_CURSOR_ROW_MODULE", "OTHER");
		Db2ModuleMembers.set(module, List.of(rowMember, new Member(Kind.TYPE, "S", null, true, true, fields)));
		assertThrows(IllegalArgumentException.class, () -> sql(module));
	}
}
