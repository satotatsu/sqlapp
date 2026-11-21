/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class InformixServerMetadataTest {
	private static class Rows {
		final List<Map<String, Object>> rows = new ArrayList<>();
		int queries;
		String sql;

		JdbcQueryHandler feed(SqlNode node, ParametersContext context, ResultSetNextHandler handler) {
			queries++;
			sql = node.eval(context).getSql();
			try {
				for (var row : rows)
					handler.handleResultSetNext(InformixMetadataReaderTest.row(row));
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
			return null;
		}
	}

	@Test
	void catalogExposesServerMetadataReaders() {
		final var catalog = DialectHolder.defaultDialect.getCatalogReader();
		assertInstanceOf(InformixTableSpaceReader.class, catalog.getTableSpaceReader());
		assertInstanceOf(InformixSettingReader.class, catalog.getSettingReader());
	}

	@Test
	void readsAllSpaceKindsAndPageSizesInOneQueryAndSurvivesXml() throws Exception {
		final var data = new Rows();
		for (int i = 0; i < 30; i++)
			data.rows.add(new HashMap<>(Map.of("space_number", Integer.toString(i + 1), "space_name",
					"space" + i + "   ", "space_owner", "informix   ", "space_flags",
					i % 3 == 0 ? "1" : i % 3 == 1 ? "16" : "-32768", "page_size", "2048")));
		final var reader = new InformixTableSpaceReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				return data.feed(n, p, h);
			}
		};
		final var spaces = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo().setMajorVersion(11));
		assertEquals(1, data.queries);
		assertEquals(30, spaces.size());
		assertEquals("DBSPACE", spaces.get(0).getSpecifics().get("INFORMIX_SPACE_KIND"));
		assertEquals("BLOBSPACE", spaces.get(1).getSpecifics().get("INFORMIX_SPACE_KIND"));
		assertEquals("SBSPACE", spaces.get(2).getSpecifics().get("INFORMIX_SPACE_KIND"));
		assertEquals("2048", spaces.get(0).getSpecifics().get("INFORMIX_PAGE_SIZE"));
		assertEquals("space0", spaces.get(0).getName());
		assertNull(spaces.get(0).getLocation());
		final var catalog = new Catalog("db");
		catalog.getTableSpaces().addAll(spaces);
		final Catalog restored = SchemaUtils.readXml(new java.io.StringReader(catalog.asXml()));
		assertEquals("SBSPACE", restored.getTableSpaces().get(2).getSpecifics().get("INFORMIX_SPACE_KIND"));
		data.rows.get(0).put("page_size", "0");
		assertThrows(IllegalStateException.class,
				() -> reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo().setMajorVersion(15)));
	}

	@Test
	void avoidsNewCatalogColumnsBeforeInformix10AndOnUnknownVersions() {
		final var data = new Rows();
		data.rows.add(
				Map.of("space_number", "1", "space_name", "rootdbs", "space_owner", "informix", "space_flags", "1"));
		final var reader = new InformixTableSpaceReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				return data.feed(n, p, h);
			}
		};
		assertFalse(reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo().setMajorVersion(9)).get(0)
				.getSpecifics().containsKey("INFORMIX_PAGE_SIZE"));
		assertFalse(data.sql.contains("pagesize"));
		reader.doGetAll(null, new ParametersContext(), null);
		assertFalse(data.sql.contains("pagesize"));
		assertTrue(data.sql.contains("sysmaster:\"informix\".sysdbspaces"));
	}

	@Test
	void distinguishesEffectiveBootAndDefaultValuesWithoutTrimmingLeadingContent() throws Exception {
		final var data = new Rows();
		final var first = new HashMap<String, Object>(Map.of("setting_id", "1", "setting_name", "DBSERVERNAME   ",
				"effective_value", " current   ", "boot_value", "boot   ", "default_value", "default   "));
		data.rows.add(first);
		data.rows.add(
				Map.of("setting_id", "2", "setting_name", "EMPTY", "effective_value", "   ", "default_value", "   "));
		final var reader = new InformixSettingReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				return data.feed(n, p, h);
			}
		};
		final var settings = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, data.queries);
		assertEquals(" current", settings.get(0).getValue());
		assertEquals("boot", settings.get(0).getSpecifics().get("INFORMIX_BOOT_VALUE"));
		assertFalse(settings.get(0).isDefault());
		assertTrue(settings.get(1).isDefault());
		assertEquals("", settings.get(1).getValue());
		final var catalog = new Catalog("db");
		catalog.getSettings().addAll(settings);
		final Catalog restored = SchemaUtils.readXml(new java.io.StringReader(catalog.asXml()));
		assertEquals(" current", restored.getSettings().get("DBSERVERNAME").getValue());
		assertEquals("boot", restored.getSettings().get("DBSERVERNAME").getSpecifics().get("INFORMIX_BOOT_VALUE"));
		first.remove("effective_value");
		assertNull(reader.doGetAll(null, new ParametersContext(), null).get(0).getValue());
		first.put("setting_name", " ");
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}
}
