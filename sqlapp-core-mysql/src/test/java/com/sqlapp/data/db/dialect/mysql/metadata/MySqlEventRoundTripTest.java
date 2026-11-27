/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
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
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mysql.sql.MySqlCreateEventFactory;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.data.schemas.EventType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class MySqlEventRoundTripTest {
	@Test
	void retrievalUsesOneBatchAndPropagatesFailures() {
		int[] queries = { 0 };
		var reader = new MySqlEventReader(dialect()) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries[0]++;
				assertTrue(node.toString().contains("INFORMATION_SCHEMA.EVENTS"));
				for (int i = 0; i < 12; i++) {
					var values = metadata();
					values.put("EVENT_NAME", "e" + i);
					try (var rows = rows(values)) {
						rows.next();
						handler.handleResultSetNext(new ExResultSet(rows));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				}
				return null;
			}
		};
		var version = new ProductVersionInfo().setMajorVersion(8).setMinorVersion(0).setRevision(0);
		assertEquals(12, reader.doGetAll(null, new ParametersContext(), version).size());
		assertEquals(1, queries[0]);
		var denied = new MySqlEventReader(dialect()) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				throw new IllegalStateException("permission denied");
			}
		};
		assertThrows(IllegalStateException.class, () -> denied.doGetAll(null, new ParametersContext(), version));
	}

	@ParameterizedTest
	@CsvSource({ "MySql,5,5", "MySql,5,7", "MySql,8,0", "MySql,8,4", "MySql,9,0" })
	void recurringMetadataRecreatesWithIdentityUnitQuotedDatesAndStatus(String product, int major, int minor)
			throws Exception {
		Dialect dialect = DialectResolver.getInstance().getDialect(product, major, minor, 0);
		var values = metadata();
		values.put("STATUS", "SLAVESIDE_DISABLED");
		Event event = read(dialect, values);
		assertEquals("def", event.getCatalogName());
		assertEquals("event db", event.getSchemaName());
		assertEquals(3, event.getIntervalValue());
		assertEquals(Timestamp.valueOf("2026-01-02 03:04:05"), event.getLastExecuted());
		String sql = sql(dialect, event);
		assertTrue(sql.startsWith("CREATE DEFINER = `root`@`localhost` EVENT"), sql);
		assertTrue(sql.contains("EVENT `event db`.`nightly event`"), sql);
		assertTrue(sql.contains("EVERY 3 DAY"), sql);
		assertTrue(sql.contains("STARTS '2030-01-02 03:04:05'"), sql);
		assertTrue(sql.contains("ENDS '2031-01-02 03:04:05'"), sql);
		assertTrue(sql.contains("ON COMPLETION NOT PRESERVE"), sql);
		assertTrue(sql.contains("DISABLE ON SLAVE"), sql);
		assertTrue(sql.contains("COMMENT 'nightly job'"), sql);
		assertTrue(sql.endsWith("DO\nBEGIN SELECT 1; SELECT 2; END"), sql);
		assertFalse(sql.contains("IF NOT EXISTS"), sql);
	}

	@ParameterizedTest
	@CsvSource({ "YEAR_MONTH,2-3", "DAY_HOUR,2 3", "DAY_MINUTE,2 3:4", "DAY_SECOND,2 3:4:5", "HOUR_MINUTE,3:4",
			"HOUR_SECOND,3:4:5", "MINUTE_SECOND,4:5", "SECOND,2147483648" })
	void compoundAndLargeIntervalsSurviveIntegerModel(String unit, String interval) throws Exception {
		Dialect dialect = dialect();
		var values = metadata();
		values.put("INTERVAL_VALUE", interval);
		values.put("INTERVAL_FIELD", unit);
		Event event = read(dialect, values);
		assertNull(event.getIntervalValue());
		assertEquals(interval, event.getSpecifics().get(MySqlCreateEventFactory.INTERVAL_VALUE));
		String quantity = unit.contains("_") ? "'" + interval + "'" : interval;
		assertTrue(sql(dialect, event).contains("EVERY " + quantity + " " + unit));
		assertTrue(sql(dialect, event.clone()).contains("EVERY " + quantity + " " + unit));
	}

	@Test
	void oneTimeMetadataAndDefinitionPrecedence() throws Exception {
		var values = metadata();
		values.put("EVENT_TYPE", "ONE TIME");
		values.put("INTERVAL_VALUE", null);
		values.put("INTERVAL_FIELD", null);
		values.put("STARTS", null);
		values.put("ENDS", null);
		values.put("EXECUTE_AT", "2030-02-03 04:05:06");
		Event event = read(dialect(), values);
		String sql = sql(dialect(), event);
		assertTrue(sql.contains("AT '2030-02-03 04:05:06'"), sql);
		assertTrue(sql.contains("ENABLE"), sql);
		assertFalse(sql.contains("EVERY"), sql);
		event.setDefinition("CREATE EVENT e ON SCHEDULE AT CURRENT_TIMESTAMP DO SELECT 1");
		event.setStatement((String) null);
		event.setIntervalValue(-1);
		assertEquals("CREATE EVENT e ON SCHEDULE AT CURRENT_TIMESTAMP DO SELECT 1", sql(dialect(), event));
	}

	@Test
	void invalidSchedulesAndCatalogStatesFailClearly() throws Exception {
		Event event = read(dialect(), metadata());
		event.setIntervalField("DAY; DROP EVENT e");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect(), event));
		event.setIntervalField("DAY");
		event.setIntervalValue(0);
		assertThrows(IllegalArgumentException.class, () -> sql(dialect(), event));
		event.setIntervalValue(1);
		event.setExecuteAt(Timestamp.valueOf("2030-01-01 00:00:00"));
		assertThrows(IllegalArgumentException.class, () -> sql(dialect(), event));
		event.setExecuteAt(null);
		event.setEventType(EventType.OneTime);
		assertThrows(IllegalArgumentException.class, () -> sql(dialect(), event));
		event.setEventType(EventType.Recurring);
		event.setOnCompletion("PRESERVE; SELECT 1");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect(), event));
		event.setOnCompletion("PRESERVE");
		event.getSpecifics().put(MySqlCreateEventFactory.INTERVAL_VALUE, "1'; SELECT 2");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect(), event));
		event.getSpecifics().clear();
		event.setEnds(Timestamp.valueOf("2020-01-01 00:00:00"));
		assertThrows(IllegalArgumentException.class, () -> sql(dialect(), event));
		event.setEnds(null);
		event.setStatement(" ");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect(), event));
		var values = metadata();
		values.put("STATUS", "UNKNOWN");
		assertThrows(SQLException.class, () -> read(dialect(), values));
	}

	private Dialect dialect() {
		return DialectResolver.getInstance().getDialect("MySql", 8, 4, 0);
	}

	private String sql(Dialect dialect, Event event) {
		SqlFactory<Event> factory = dialect.createSqlFactoryRegistry().getSqlFactory(event, SqlType.CREATE);
		return factory.createSql(event).get(0).getSqlText().replace("\r\n", "\n");
	}

	private Event read(Dialect dialect, Map<String, Object> values) throws Exception {
		try (var rows = rows(values)) {
			rows.next();
			return new MySqlEventReader(dialect).createEvent(new ExResultSet(rows),
					new ProductVersionInfo().setMajorVersion(8).setMinorVersion(0).setRevision(0));
		}
	}

	private CachedRowSet rows(Map<String, Object> values) throws SQLException {
		RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
		metadata.setColumnCount(values.size());
		int i = 0;
		var dates = List.of("EXECUTE_AT", "STARTS", "ENDS", "CREATED", "LAST_ALTERED", "LAST_EXECUTED");
		for (String key : values.keySet()) {
			metadata.setColumnName(++i, key);
			metadata.setColumnType(i, dates.contains(key) ? Types.TIMESTAMP : Types.VARCHAR);
		}
		CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
		try {
			rows.setMetaData(metadata);
			rows.moveToInsertRow();
			i = 0;
			for (var entry : values.entrySet()) {
				Object value = entry.getValue();
				if (value != null && dates.contains(entry.getKey()))
					value = Timestamp.valueOf(value.toString());
				rows.updateObject(++i, value);
			}
			rows.insertRow();
			rows.moveToCurrentRow();
			rows.beforeFirst();
			return rows;
		} catch (SQLException e) {
			rows.close();
			throw e;
		}
	}

	private Map<String, Object> metadata() {
		Map<String, Object> values = new LinkedHashMap<>();
		String[] pairs = { "EVENT_NAME", "nightly event", "EVENT_SCHEMA", "event db", "EVENT_CATALOG", "def", "DEFINER",
				"root@localhost", "INTERVAL_FIELD", "DAY", "INTERVAL_VALUE", "3", "EVENT_DEFINITION",
				"BEGIN SELECT 1; SELECT 2; END", "EVENT_TYPE", "RECURRING", "ON_COMPLETION", "NOT PRESERVE", "STARTS",
				"2030-01-02 03:04:05", "ENDS", "2031-01-02 03:04:05", "STATUS", "ENABLED", "EVENT_COMMENT",
				"nightly job", "LAST_EXECUTED", "2026-01-02 03:04:05" };
		for (int i = 0; i < pairs.length; i += 2)
			values.put(pairs[i], pairs[i + 1]);
		for (String key : List.of("EXECUTE_AT", "CREATED", "LAST_ALTERED"))
			values.put(key, null);
		return values;
	}
}
