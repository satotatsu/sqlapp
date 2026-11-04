/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerCreateSequenceFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SpannerSequenceReaderTest {
	private class Reader extends SpannerSequenceReader {
		final List<Map<String, String>> rows = new ArrayList<>();
		int queries;

		Reader() {
			super(DialectHolder.defaultDialect);
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			try {
				for (var values : rows)
					h.handleResultSetNext(new ExResultSet(null) {
						private String last;

						@Override
						public String getString(String name) {
							return values.get(name);
						}

						@Override
						public long getLong(String name) {
							last = values.get(name);
							return last == null ? 0 : Long.parseLong(last);
						}

						@Override
						public boolean wasNull() {
							return last == null;
						}
					});
			} catch (java.sql.SQLException exception) {
				throw new IllegalStateException(exception);
			}
			return null;
		}
	}

	private Map<String, String> row(String schema, String name) {
		return new HashMap<>(Map.of("CATALOG", "", "SCHEMA", schema, "NAME", name, "DATA_TYPE", "INT64",
				"START_WITH_COUNTER", "3000", "SKIP_RANGE_MIN", "100", "SKIP_RANGE_MAX", "200", "SEQUENCE_KIND",
				"bit_reversed_positive"));
	}

	@Test
	void readsFortySequencesInOneQueryAndPreservesOptionsThroughXmlAndDdl() throws Exception {
		final var reader = new Reader();
		for (String schema : List.of("a", "b"))
			for (int i = 0; i < 20; i++)
				reader.rows.add(row(schema, "sequence " + i));
		final var sequences = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(40, sequences.size());
		assertEquals(1, reader.queries);
		for (var sequence : sequences) {
			final Sequence restored = SchemaUtils.readXml(new java.io.StringReader(sequence.asXml()));
			assertEquals(sequence.getSchemaName(), restored.getSchemaName());
			assertEquals(sequence.getStartValue(), restored.getStartValue());
			assertEquals("bit_reversed_positive", restored.getSpecifics().get("SEQUENCE_KIND"));
			assertEquals("100", restored.getSpecifics().get(SpannerCreateSequenceFactory.SKIP_RANGE_MIN));
			assertEquals("200", restored.getSpecifics().get(SpannerCreateSequenceFactory.SKIP_RANGE_MAX));
			final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			final String sql = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("START COUNTER WITH 3000"), sql);
			assertTrue(sql.contains("SKIP RANGE 100, 200"), sql);
			assertTrue(sql.contains("BIT_REVERSED_POSITIVE"), sql);
		}
	}

	@Test
	void rejectsMissingNamesDuplicateIdentitiesAndInvalidCountersOrRanges() {
		for (int mode = 0; mode < 8; mode++) {
			final var reader = new Reader();
			final var values = row("app", "ids");
			switch (mode) {
			case 0 -> values.remove("NAME");
			case 1 -> values.put("NAME", "  ");
			case 2 -> values.put("START_WITH_COUNTER", "0");
			case 3 -> values.put("START_WITH_COUNTER", "-1");
			case 4 -> values.remove("SKIP_RANGE_MIN");
			case 5 -> values.remove("SKIP_RANGE_MAX");
			case 6 -> values.put("SKIP_RANGE_MIN", "201");
			case 7 -> reader.rows.add(row("app", "ids"));
			}
			reader.rows.add(values);
			assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		}
	}

	@Test
	void allowsMissingOptionalOptionsAndSingleValueSkipRanges() {
		final var reader = new Reader();
		final var plain = row("app", "plain");
		plain.remove("START_WITH_COUNTER");
		plain.remove("SKIP_RANGE_MIN");
		plain.remove("SKIP_RANGE_MAX");
		plain.remove("SEQUENCE_KIND");
		reader.rows.add(plain);
		final var single = row("app", "single");
		single.put("SKIP_RANGE_MAX", "100");
		reader.rows.add(single);
		final var sequences = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(2, sequences.size());
		assertNull(sequences.get(0).getSpecifics().get(SpannerCreateSequenceFactory.SKIP_RANGE_MIN));
		assertEquals("100", sequences.get(1).getSpecifics().get(SpannerCreateSequenceFactory.SKIP_RANGE_MAX));
		assertNull(sequences.get(0).getSpecifics().get("SEQUENCE_KIND"));
	}

	@Test
	void acceptsKnownRawAndSqlLiteralKindsButRejectsUnknownOrMalformedValues() {
		for (String kind : List.of("bit_reversed_positive", "BIT_REVERSED_POSITIVE", "'bit_reversed_positive'",
				"\"bit_reversed_positive\"")) {
			final var reader = new Reader();
			final var values = row("app", "ids");
			values.put("SEQUENCE_KIND", kind);
			reader.rows.add(values);
			assertEquals("bit_reversed_positive",
					reader.doGetAll(null, new ParametersContext(), null).get(0).getSpecifics().get("SEQUENCE_KIND"));
		}
		for (String kind : List.of("", "monotonic", "'bit_reversed_positive", "'bit_reversed_positive' || 'other'")) {
			final var reader = new Reader();
			final var values = row("app", "ids");
			values.put("SEQUENCE_KIND", kind);
			reader.rows.add(values);
			assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		}
	}

	@Test
	void queryIncludesKindUsingExistingOptionTableAndBindsObjectFilters() {
		final var context = new ParametersContext();
		context.put("schemaName", List.of("app' OR TRUE"));
		context.put("sequenceName", List.of("ids"));
		final var parameters = new SqlParameterCollection();
		new Reader().getSqlNode(null).eval(context, parameters);
		assertTrue(parameters.getSql().contains("opt.OPTION_NAME='sequence_kind'"));
		assertFalse(parameters.getSql().contains("app' OR TRUE"));
		assertEquals(2, parameters.getParameterSize());
	}
}
