package com.sqlapp.data.db.dialect.vertica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.vertica.metadata.VerticaSchedulingStateRestorer;
import com.sqlapp.data.schemas.Schema;

class VerticaSchedulingStateRestorerTest {
	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	private Schema schema() {
		Schema schema = new Schema("app");
		schema.getSpecifics().put("VERTICA_SCHEDULING_STATES",
				"TRIGGER \"app\".\"t\" ENABLED=false; SCHEDULE \"app\".\"s\" ENABLED=false; ");
		return schema;
	}

	static class Jdbc {
		Map<String, Boolean> states = new LinkedHashMap<>(Map.of("TRIGGER:t", true, "SCHEDULE:s", false));
		String scheduler = "0";
		boolean autoCommit = true;
		boolean ignoreWrite;
		int bulkReads, configReads, mutations;
		int failMutation;

		Connection connection() {
			return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
			case "getAutoCommit" -> autoCommit;
			case "prepareStatement" -> statement((String) a[0]);
			default -> throw new AssertionError(m.getName());
			});
		}

		PreparedStatement statement(String sql) {
			Map<Integer, Object> params = new HashMap<>();
			return proxy(PreparedStatement.class, (p, m, a) -> switch (m.getName()) {
			case "setString", "setBoolean" -> {
				params.put((Integer) a[0], a[1]);
				yield null;
			}
			case "close" -> null;
			case "executeQuery" -> {
				if (sql.contains("GET_CONFIG_PARAMETER")) {
					configReads++;
					yield rows(Collections.singletonList(new Object[] { scheduler }));
				}
				if (sql.contains("UNION ALL")) {
					bulkReads++;
					assertEquals(Map.of(1, "app", 2, "app"), params);
					var data = new ArrayList<Object[]>();
					states.forEach((key, value) -> {
						String[] parts = key.split(":");
						data.add(new Object[] { parts[0], "app", parts[1], value });
					});
					yield rows(data);
				}
				assertTrue(sql.equals("SELECT ENABLE_TRIGGER(?, ?)") || sql.equals("SELECT ENABLE_SCHEDULE(?, ?)"),
						sql);
				mutations++;
				if (mutations == failMutation)
					throw new SQLException("test mutation failure");
				String kind = sql.contains("ENABLE_TRIGGER") ? "TRIGGER" : "SCHEDULE";
				String key = kind + ":" + (kind.equals("TRIGGER") ? "t" : "s");
				assertEquals("\"app\".\"" + key.split(":")[1] + "\"", params.get(1));
				if (!ignoreWrite)
					states.put(key, (Boolean) params.get(2));
				yield rows(List.of());
			}
			default -> throw new AssertionError(m.getName());
			});
		}

		ResultSet rows(List<Object[]> data) {
			int[] position = { -1 };
			boolean[] wasNull = { false };
			return proxy(ResultSet.class, (p, m, a) -> switch (m.getName()) {
			case "next" -> ++position[0] < data.size();
			case "getString" -> {
				Object value = data.get(position[0])[(Integer) a[0] - 1];
				wasNull[0] = value == null;
				yield (String) value;
			}
			case "getLong" -> {
				Object value = data.get(position[0])[(Integer) a[0] - 1];
				wasNull[0] = value == null;
				yield value == null ? 0L : ((Number) value).longValue();
			}
			case "getBoolean" -> {
				Object value = data.get(position[0])[(Integer) a[0] - 1];
				wasNull[0] = value == null;
				yield value == null ? false : (Boolean) value;
			}
			case "wasNull" -> wasNull[0];
			case "close" -> null;
			default -> throw new AssertionError(m.getName());
			});
		}
	}

	@Test
	void onlyDifferencesAreChangedAndRepeatedRestoreIsANoop() throws Exception {
		Jdbc jdbc = new Jdbc();
		var result = new VerticaSchedulingStateRestorer().restore(jdbc.connection(), schema());
		assertEquals(1, result.applied().size());
		assertEquals(1, result.unchanged());
		assertEquals(2, jdbc.bulkReads);
		assertEquals(1, jdbc.mutations);
		assertEquals(0, new VerticaSchedulingStateRestorer().restore(jdbc.connection(), schema()).applied().size());
		assertEquals(1, jdbc.mutations);
	}

	@Test
	void runningSchedulerTransactionsAndMissingObjectsFailBeforeMutation() {
		for (int mode = 0; mode < 3; mode++) {
			Jdbc jdbc = new Jdbc();
			if (mode == 0)
				jdbc.scheduler = "1";
			if (mode == 1)
				jdbc.autoCommit = false;
			if (mode == 2)
				jdbc.states.remove("TRIGGER:t");
			assertThrows(SQLException.class,
					() -> new VerticaSchedulingStateRestorer().restore(jdbc.connection(), schema()));
			assertEquals(0, jdbc.mutations);
		}
	}

	@Test
	void partialMutationAndVerificationFailuresRetainCompletedChanges() {
		Jdbc jdbc = new Jdbc();
		jdbc.states.put("SCHEDULE:s", true);
		jdbc.failMutation = 2;
		var failure = assertThrows(VerticaSchedulingStateRestorer.RestorationException.class,
				() -> new VerticaSchedulingStateRestorer().restore(jdbc.connection(), schema()));
		assertEquals(1, failure.getCompleted().applied().size());
		assertEquals("SCHEDULE", failure.getFailedChange().kind());
		Jdbc ignored = new Jdbc();
		ignored.ignoreWrite = true;
		var verification = assertThrows(VerticaSchedulingStateRestorer.RestorationException.class,
				() -> new VerticaSchedulingStateRestorer().restore(ignored.connection(), schema()));
		assertNull(verification.getFailedChange());
		assertEquals(1, verification.getCompleted().applied().size());
	}

	@Test
	void parserRejectsAmbiguityAndDecodesExporterEscapes() {
		var parsed = VerticaSchedulingStateRestorer
				.parse("TRIGGER \"app\".\"quote\"\"slash\\\\line\\n\" ENABLED=false;", "app");
		assertEquals("quote\"slash\\line\n", parsed.values().iterator().next().name());
		for (String snapshot : List.of("TRIGGER \"other\".\"t\" ENABLED=false;", "TRIGGER \"app\".\"t\" ENABLED=maybe;",
				"TRIGGER \"app\".\"t\" ENABLED=false; TRIGGER \"app\".\"T\" ENABLED=true;",
				"TRIGGER app.t ENABLED=false;"))
			assertThrows(IllegalArgumentException.class, () -> VerticaSchedulingStateRestorer.parse(snapshot, "app"));
	}
}
