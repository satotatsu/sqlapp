/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mysql.util.MySqlDefiner;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.jdbc.ExResultSet;

class MySqlDefinerRoundTripTest {
	@ParameterizedTest
	@CsvSource({ "5,5", "5,7", "8,0", "8,4", "9,0" })
	void catalogIdentitySurvivesAcrossStoredObjects(int major, int minor) throws Exception {
		var dialect = DialectResolver.getInstance().getDialect("MySql", major, minor, 0);
		for (String raw : List.of("owner@localhost", "O'Reilly`\\owner@10.0.%", "@localhost", "owner@",
				"'owner'@'host'", " owner @host ")) {
			for (var object : objects(dialect, raw)) {
				String expected = raw.replace("`", "``").replace("@", "`@`");
				String sql = sql(dialect, object);
				assertTrue(sql.startsWith("CREATE DEFINER = `" + expected + "` "), sql);
				assertEquals(sql.indexOf("DEFINER ="), sql.lastIndexOf("DEFINER ="), sql);
			}
		}
	}

	@Test
	void ambiguousRawIdentitiesRequireExplicitConfigurationAndDefinitionsTakePrecedence() throws Exception {
		var dialect = DialectResolver.getInstance().getDialect("MySql", 8, 4, 0);
		for (var object : objects(dialect, "owner@work@localhost")) {
			var error = assertThrows(IllegalArgumentException.class, () -> sql(dialect, object));
			assertTrue(error.getMessage().contains(MySqlDefiner.DEFINER));
			object.getSpecifics().put(MySqlDefiner.DEFINER, "'owner@work'@'localhost'");
			assertTrue(sql(dialect, object).startsWith("CREATE DEFINER = `owner@work`@`localhost` "));
			object.getSpecifics().put(MySqlDefiner.DEFINER, "owner");
			assertThrows(IllegalArgumentException.class, () -> sql(dialect, object));
			object.setDefinition("CREATE /* complete definition wins */");
			assertEquals("CREATE /* complete definition wins */", sql(dialect, object));
		}
		for (var object : objects(dialect, null))
			assertFalse(sql(dialect, object).contains("DEFINER ="));
	}

	private List<AbstractSchemaObject<?>> objects(Dialect dialect, String raw) throws Exception {
		try (var rows = rows(raw)) {
			var rs = new ExResultSet(rows);
			var view = new MySqlViewReader(dialect).createTable(rs);
			var trigger = new MySqlTriggerReader(dialect).createTrigger(rs);
			Function function = new Function("f");
			function.getReturning().setDataType(DataType.INT);
			MySqlUtils.setRoutineInfo(rs, function);
			function.setStatement("RETURN 1");
			Procedure procedure = new Procedure("p");
			MySqlUtils.setRoutineInfo(rs, procedure);
			Event event = new Event("e");
			event.setDefiner(raw);
			event.setStatement("SELECT 1");
			event.setIntervalValue(1);
			event.setIntervalField("DAY");
			return List.of(view, trigger, function, procedure, event);
		}
	}

	private String sql(Dialect dialect, AbstractSchemaObject<?> object) {
		SqlFactory<AbstractSchemaObject<?>> factory = dialect.createSqlFactoryRegistry().getSqlFactory(object,
				SqlType.CREATE);
		return factory.createSql(object).get(0).getSqlText();
	}

	private CachedRowSet rows(String raw) throws SQLException {
		Map<String, Object> values = new LinkedHashMap<>();
		String[] pairs = { "TABLE_NAME", "v", "TABLE_CATALOG", "def", "TABLE_SCHEMA", "app", "VIEW_DEFINITION",
				"SELECT 1", "IS_UPDATABLE", "YES", "CHECK_OPTION", "NONE", "SECURITY_TYPE", "DEFINER", "TRIGGER_NAME",
				"t", "TRIGGER_CATALOG", "def", "TRIGGER_SCHEMA", "app", "EVENT_MANIPULATION", "INSERT",
				"EVENT_OBJECT_SCHEMA", "app", "EVENT_OBJECT_TABLE", "orders", "ACTION_STATEMENT", "SET NEW.id = 1",
				"ACTION_TIMING", "BEFORE", "ACTION_ORIENTATION", "ROW", "ROUTINE_CATALOG", "def", "ROUTINE_SCHEMA",
				"app", "ROUTINE_DEFINITION", "SELECT 1", "IS_DETERMINISTIC", "YES", "SQL_DATA_ACCESS", "NO SQL" };
		for (int i = 0; i < pairs.length; i += 2)
			values.put(pairs[i], pairs[i + 1]);
		values.put("DEFINER", raw);
		for (String key : List.of("CREATED", "LAST_ALTERED", "ROUTINE_COMMENT"))
			values.put(key, null);
		RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
		metadata.setColumnCount(values.size());
		int index = 0;
		for (String key : values.keySet()) {
			metadata.setColumnName(++index, key);
			metadata.setColumnType(index, Types.VARCHAR);
		}
		CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
		rows.setMetaData(metadata);
		rows.moveToInsertRow();
		index = 0;
		for (Object value : values.values())
			rows.updateObject(++index, value);
		rows.insertRow();
		rows.moveToCurrentRow();
		rows.beforeFirst();
		rows.next();
		return rows;
	}
}
