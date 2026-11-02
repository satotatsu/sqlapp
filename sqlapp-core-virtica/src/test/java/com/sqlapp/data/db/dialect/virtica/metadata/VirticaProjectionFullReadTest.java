package com.sqlapp.data.db.dialect.virtica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.virtica.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class VirticaProjectionFullReadTest {
	private static class Reader extends VirticaProjectionReader {
		int catalogs;
		int count = 3;
		String selected;

		Reader() {
			super(DialectHolder.defaultDialect12_0_4);
		}

		@Override
		public ProductVersionInfo getProductVersionInfo(Connection c) {
			return new ProductVersionInfo();
		}

		@Override
		protected ParametersContext defaultParametersContext(Connection c) {
			return new ParametersContext();
		}

		@Override
		protected boolean filterObject(Projection p) {
			return selected == null || selected.equals(p.getName());
		}

		@Override
		protected List<Projection> doGetAll(Connection c, ParametersContext context, ProductVersionInfo version) {
			catalogs++;
			List<Projection> result = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				Projection p = new Projection("p" + i);
				p.setSchemaName("app");
				result.add(p);
			}
			return result;
		}
	}

	private static class Jdbc {
		int exports;
		int position;
		String scope;
		String script = "CREATE PROJECTION app.p0 AS SELECT id FROM app.t; CREATE PROJECTION app.p1 AS SELECT id FROM app.t; CREATE PROJECTION app.p2 AS SELECT id FROM app.t;";

		@SuppressWarnings("unchecked")
		private <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
			return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
		}

		Connection connection() {
			return proxy(Connection.class, (p, m, a) -> {
				assertEquals("prepareStatement", m.getName());
				assertEquals("SELECT EXPORT_OBJECTS('', ?, false)", a[0]);
				return proxy(PreparedStatement.class, (sp, sm, sa) -> switch (sm.getName()) {
				case "setString" -> {
					scope = (String) sa[1];
					yield null;
				}
				case "close" -> null;
				case "executeQuery" -> {
					exports++;
					yield proxy(ResultSet.class, (rp, rm, ra) -> switch (rm.getName()) {
					case "next" -> ++position == 1;
					case "getString" -> script;
					case "close" -> null;
					default -> throw new AssertionError(rm.getName());
					});
				}
				default -> throw new AssertionError(sm.getName());
				});
			});
		}
	}

	@Test
	void ordinaryReadIsMetadataOnlyAndFullReadExportsTheSetOnce() {
		Reader ordinary = new Reader();
		var metadata = ordinary.getAll(null);
		assertEquals(1, ordinary.catalogs);
		assertEquals(3, metadata.size());
		assertTrue(metadata.get(0).getDefinition() == null || metadata.get(0).getDefinition().isEmpty());
		Reader full = new Reader();
		Jdbc jdbc = new Jdbc();
		var objects = full.getAllFull(jdbc.connection());
		assertEquals(1, full.catalogs);
		assertEquals(1, jdbc.exports);
		assertEquals("\"app\".\"p0\",\"app\".\"p1\",\"app\".\"p2\"", jdbc.scope);
		assertEquals(3, objects.size());
		objects.forEach(p -> assertTrue(String.join("\n", p.getDefinition()).contains("app." + p.getName())));
	}

	@Test
	void filtersAreAppliedBeforeExportAndEmptyReadsDoNotExport() {
		Reader filtered = new Reader();
		filtered.selected = "p1";
		Jdbc jdbc = new Jdbc();
		assertEquals(1, filtered.getAllFull(jdbc.connection()).size());
		assertEquals("\"app\".\"p1\"", jdbc.scope);
		Reader empty = new Reader();
		empty.count = 0;
		assertTrue(empty.getAllFull(null).isEmpty());
		Reader excluded = new Reader();
		excluded.selected = "absent";
		assertTrue(excluded.getAllFull(null).isEmpty());
	}

	@Test
	void failedFullReadDoesNotPartiallyPopulateSchema() {
		Reader reader = new Reader();
		Jdbc jdbc = new Jdbc();
		jdbc.script = "CREATE PROJECTION app.p0 AS SELECT id FROM app.t;";
		Schema schema = new Schema("app");
		Projection existing = new Projection("existing");
		existing.setDefinition("original");
		schema.getProjections().add(existing);
		assertThrows(IllegalArgumentException.class, () -> reader.loadFull(jdbc.connection(), schema));
		assertEquals(1, jdbc.exports);
		assertEquals(1, schema.getProjections().size());
		assertSame(existing, schema.getProjections().get(0));
		assertEquals(List.of("original"), existing.getDefinition());
	}
}
