/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class Db2RowTypeSqlTest {
	private Type row() {
		var type = new Type("R.Name").setSchemaName("A.Schema").setMetaType(Type.MetaType.Row);
		type.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		var field = new TypeColumn("F\"Name").setDataType(DataType.VARCHAR).setLength(20L);
		field.getSpecifics().putAll(Map.of("TYPENAME", "VARCHAR", "DB2_TYPE_SCHEMA", "SYSIBM", "CODEPAGE", "1208",
				"TYPESTRINGUNITS", "CODEUNITS32", "STRINGUNITSLENGTH", "5"));
		type.getColumns().add(field);
		return type;
	}

	private List<String> sql(Type type, SqlType operation) {
		SqlFactory<Type> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(type,
				operation);
		return factory.createSql(type).stream().map(SqlOperation::getSqlText).toList();
	}

	@Test
	void rowFieldOrderNamesUnitsAndCommentsSurviveXmlAndClone() throws Exception {
		var row = row();
		row.setRemarks("row's comment");
		Type restored = SchemaUtils.readXml(new java.io.StringReader(row.asXml()));
		assertEquals(row, restored);
		assertEquals(row, row.clone());
		assertEquals(
				"CREATE TYPE \"A.Schema\".\"R.Name\" AS ROW (\"ID\" INTEGER, \"F\"\"Name\" VARCHAR(5 CODEUNITS32))",
				sql(restored, SqlType.CREATE).getFirst());
		assertEquals("COMMENT ON TYPE \"A.Schema\".\"R.Name\" IS 'row''s comment'",
				sql(restored, SqlType.CREATE).getLast());
		assertEquals(List.of("DROP TYPE \"A.Schema\".\"R.Name\""), sql(row, SqlType.DROP));
	}

	@Test
	void cursorStructuredAndNestedFieldsRequireCompleteDdlAndDefinitionsAreAuthoritative() {
		var row = row();
		row.getColumns().getFirst().getSpecifics().put("DB2_TYPE_SCHEMA", "CUSTOM");
		assertThrows(IllegalArgumentException.class, () -> sql(row, SqlType.CREATE));
		String ddl = "CREATE TYPE \"A.Schema\".\"R.Name\" AS ROW (N CUSTOM.T)";
		row.getDefinition().add(ddl);
		assertEquals(List.of(ddl), sql(row, SqlType.CREATE));
		var cursor = new Type("C").setSchemaName("APP").setMetaType(Type.MetaType.Cursor);
		assertThrows(IllegalArgumentException.class, () -> sql(cursor, SqlType.CREATE));
		cursor.getDefinition().add("CREATE TYPE APP.C AS CURSOR");
		assertEquals(cursor.getDefinition(), sql(cursor, SqlType.CREATE));
		var structured = new Type("S").setSchemaName("APP");
		assertThrows(IllegalArgumentException.class, () -> sql(structured, SqlType.CREATE));
	}

	@Test
	void reservedRowConstraintsAndImplicitReplacementsAreRejected() {
		var row = row();
		row.getColumns().getFirst().setNotNull(true);
		assertThrows(IllegalArgumentException.class, () -> sql(row, SqlType.CREATE));
		row.getColumns().getFirst().setNotNull(false);
		SqlFactory<Type> alter = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(row,
				SqlType.ALTER);
		var target = row.clone().setRemarks("modified");
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(row.diff(target)));
		assertTrue(alter.createDiffSql(row.diff(row.clone())).isEmpty());
		SqlFactory<Type> old = DialectHolder.Db2_1050Dialect.createSqlFactoryRegistry().getSqlFactory(row,
				SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> old.createSql(row));
	}

	@Test
	void catalogCreatesRowTypesBeforeRoutinesThatUseThem() {
		var catalog = new Catalog();
		var schema = new Schema("A.Schema");
		catalog.getSchemas().add(schema);
		schema.getTypes().add(row());
		var routine = new Procedure("P");
		String ddl = "CREATE PROCEDURE \"A.Schema\".P(IN V \"A.Schema\".\"R.Name\") LANGUAGE SQL BEGIN END";
		routine.getDefinition().add(ddl);
		schema.getProcedures().add(routine);
		SqlFactory<Catalog> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(catalog,
				SqlType.CREATE);
		var sql = factory.createSql(catalog).stream().map(SqlOperation::getSqlText).toList();
		assertTrue(sql.indexOf(sql(row(), SqlType.CREATE).getFirst()) < sql.indexOf(ddl), sql.toString());
	}
}
