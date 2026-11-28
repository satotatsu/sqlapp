package com.sqlapp.data.db.dialect.oracle;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

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

class OracleDefaultExpressionSupportTest {
	private final DefaultExpressionSupport support = DialectResolver.getInstance().getDialect("Oracle", 23, 0, 0)
			.getDefaultExpressionSupport();

	private TemporalSemantics temporal(final String sql, final DataType type) {
		return assertInstanceOf(DefaultExpression.CurrentTime.class,
				support.parse(sql, new Column("c").setDataType(type)), sql).semantics();
	}

	/** Forms read from Oracle 26ai Free. */
	@Test
	void recognizesStoredForms() {
		assertInstanceOf(OracleDefaultExpressionSupport.class, support);
		assertEquals(new TemporalSemantics(EvaluationPoint.STATEMENT_START, TimeBasis.SERVER_CLOCK,
				TemporalKind.DATETIME, 0), temporal("SYSDATE", DataType.DATETIME));
		assertEquals(TimeBasis.SESSION_TIME_ZONE, temporal("CURRENT_DATE", DataType.DATETIME).basis());
		assertEquals(TemporalKind.DATE, temporal("TRUNC(SYSDATE)", DataType.DATETIME).kind());
		final var local = temporal("SYSTIMESTAMP", DataType.TIMESTAMP);
		assertEquals(TimeBasis.SERVER_CLOCK, local.basis());
		assertEquals(TemporalKind.DATETIME, local.kind());
		final var instant = temporal("SYSTIMESTAMP", DataType.TIMESTAMP_WITH_TIMEZONE);
		assertEquals(TimeBasis.INSTANT, instant.basis());
		assertEquals(TimeBasis.UTC, temporal("SYS_EXTRACT_UTC(SYSTIMESTAMP)", DataType.TIMESTAMP).basis());
		assertEquals(3, temporal("CURRENT_TIMESTAMP(3)", DataType.TIMESTAMP_WITH_TIMEZONE).precision());
		final Column nvarchar = new Column("c").setDataType(DataType.NVARCHAR);
		assertEquals(new DefaultExpression.StringLiteral("x"), support.parse("u'x'", nvarchar));
		assertInstanceOf(DefaultExpression.Unrecognized.class, support.parse("u'\\00e9'", nvarchar));
		assertEquals(new DefaultExpression.BooleanLiteral(true), support.parse("TRUE", nvarchar));
		assertInstanceOf(DefaultExpression.Unrecognized.class, support.parse("SYS_GUID()", nvarchar));
		assertInstanceOf(DefaultExpression.Unrecognized.class, support.parse("1+2", nvarchar));
	}

	@Test
	void offersCandidatesThatReadBackWithTheirSemantics() {
		for (final TemporalKind kind : List.of(TemporalKind.DATETIME, TemporalKind.DATE,
				TemporalKind.DATETIME_WITH_TIME_ZONE)) {
			final DataType type = switch (kind) {
			case DATETIME -> DataType.TIMESTAMP;
			case DATE -> DataType.DATE;
			default -> DataType.TIMESTAMP_WITH_TIMEZONE;
			};
			final var candidates = support.temporalCandidates(kind);
			assertFalse(candidates.isEmpty(), kind.name());
			for (final TemporalCandidate candidate : candidates)
				assertEquals(candidate.semantics(), temporal(candidate.sql(), type), candidate.sql());
		}
		assertTrue(support.temporalCandidates(TemporalKind.TIME).isEmpty());
	}

	@Test
	void booleansDependOnTheVersion() {
		final Column column = new Column("c").setDataType(DataType.BOOLEAN);
		assertEquals("TRUE", support.booleanLiteral(true, column));
		assertEquals("1", DialectResolver.getInstance().getDialect("Oracle", 19, 0, 0).getDefaultExpressionSupport()
				.booleanLiteral(true, column));
		assertEquals("N'a''b'", support.stringLiteral("a'b", new Column("c").setDataType(DataType.NVARCHAR)));
	}
}
