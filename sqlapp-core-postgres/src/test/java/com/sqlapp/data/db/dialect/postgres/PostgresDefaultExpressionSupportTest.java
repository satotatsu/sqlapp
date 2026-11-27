package com.sqlapp.data.db.dialect.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpression;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionSupport;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalCandidate;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.EvaluationPoint;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TemporalKind;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TimeBasis;
import com.sqlapp.data.schemas.Column;

class PostgresDefaultExpressionSupportTest {

	private final DefaultExpressionSupport support = DialectResolver.getInstance().getDialect("PostgreSQL", 17, 0, 0)
			.getDefaultExpressionSupport();

	private DefaultExpression parse(final String sql) {
		return support.parse(sql, new Column("c").setDataType(DataType.VARCHAR));
	}

	private TemporalSemantics temporal(final String sql) {
		return assertInstanceOf(DefaultExpression.CurrentTime.class, parse(sql)).semantics();
	}

	/** Forms as returned by pg_get_expr on PostgreSQL 17. */
	@Test
	void recognizesStoredForms() {
		assertInstanceOf(PostgresDefaultExpressionSupport.class, support);
		final var transaction = new TemporalSemantics(EvaluationPoint.TRANSACTION_START, TimeBasis.INSTANT,
				TemporalKind.DATETIME_WITH_TIME_ZONE, 6);
		assertEquals(transaction, temporal("CURRENT_TIMESTAMP"));
		assertEquals(transaction, temporal("now()"));
		assertEquals(3, temporal("CURRENT_TIMESTAMP(3)").precision());
		assertEquals(EvaluationPoint.STATEMENT_START, temporal("statement_timestamp()").point());
		assertEquals(EvaluationPoint.CALL, temporal("clock_timestamp()").point());
		assertEquals(new TemporalSemantics(EvaluationPoint.TRANSACTION_START, TimeBasis.SESSION_TIME_ZONE,
				TemporalKind.DATETIME, 3), temporal("LOCALTIMESTAMP(3)"));
		assertEquals(TemporalKind.DATE, temporal("CURRENT_DATE").kind());
		final var cast = temporal("(statement_timestamp())::timestamp without time zone");
		assertEquals(new TemporalSemantics(EvaluationPoint.STATEMENT_START, TimeBasis.SESSION_TIME_ZONE,
				TemporalKind.DATETIME, 6), cast);
		assertEquals(TemporalKind.DATE, temporal("(statement_timestamp())::date").kind());
		final var utc = temporal("(statement_timestamp() AT TIME ZONE 'UTC'::text)");
		assertEquals(TimeBasis.UTC, utc.basis());
		assertEquals(TemporalKind.DATETIME, utc.kind());

		assertEquals(new DefaultExpression.StringLiteral("a'b"), parse("'a''b'::character varying"));
		assertEquals(new DefaultExpression.StringLiteral("NULL"), parse("'NULL'::character varying"));
		assertEquals(new DefaultExpression.StringLiteral("ab"), parse("'ab'::bpchar"));
		assertEquals(new DefaultExpression.NumericLiteral("-5"), parse("'-5'::integer"));
		assertEquals(new DefaultExpression.NumericLiteral("1.5"), parse("1.5"));
		assertEquals(new DefaultExpression.BooleanLiteral(true), parse("true"));
		assertInstanceOf(DefaultExpression.Unrecognized.class, parse("'2000-01-01'::date"));
		assertInstanceOf(DefaultExpression.Unrecognized.class, parse("gen_random_uuid()"));
	}

	@Test
	void serialDefaultBelongsToTheGenerator() {
		final Column serial = new Column("id").setDataType(DataType.INT).setIdentity(true);
		assertTrue(support.isGeneratorDefault("nextval('p_v_seq'::regclass)", serial));
		assertFalse(
				support.isGeneratorDefault("nextval('p_v_seq'::regclass)", new Column("x").setDataType(DataType.INT)));
	}

	@Test
	void offersCandidatesForEachColumnKind() {
		assertEquals("CURRENT_TIMESTAMP", support
				.temporalCandidates(new Column("c").setDataType(DataType.TIMESTAMP_WITH_TIMEZONE)).getFirst().sql());
		for (final DataType type : new DataType[] { DataType.TIMESTAMP, DataType.DATETIME, DataType.DATE, DataType.TIME,
				DataType.TIME_WITH_TIMEZONE }) {
			final var candidates = support.temporalCandidates(new Column("c").setDataType(type));
			assertFalse(candidates.isEmpty(), type.name());
			assertTrue(candidates.stream().map(TemporalCandidate::semantics)
					.allMatch(s -> s.kind() == TemporalKind.of(type)), type.name());
		}
	}
}
