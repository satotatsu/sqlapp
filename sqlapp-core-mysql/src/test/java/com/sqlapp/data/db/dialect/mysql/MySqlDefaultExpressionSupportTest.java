package com.sqlapp.data.db.dialect.mysql;

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

class MySqlDefaultExpressionSupportTest {
	private final DefaultExpressionSupport support = DialectResolver.getInstance().getDialect("mysql", 8, 4, 0)
			.getDefaultExpressionSupport();

	private DefaultExpression parse(final String sql) {
		return support.parse(sql, new Column("c").setDataType(DataType.TIMESTAMP));
	}

	private TemporalSemantics temporal(final String sql) {
		return assertInstanceOf(DefaultExpression.CurrentTime.class, parse(sql), sql).semantics();
	}

	/** Forms read from MySQL 8.4 and MariaDB 11.8. */
	@Test
	void recognizesStoredForms() {
		assertInstanceOf(MySqlDefaultExpressionSupport.class, support);
		assertEquals(new TemporalSemantics(EvaluationPoint.STATEMENT_START, TimeBasis.SESSION_TIME_ZONE,
				TemporalKind.DATETIME, 0), temporal("CURRENT_TIMESTAMP"));
		assertEquals(3, temporal("CURRENT_TIMESTAMP(3)").precision());
		assertEquals(3, temporal("current_timestamp(3)").precision());
		assertEquals(0, temporal("current_timestamp()").precision());
		assertEquals(TemporalKind.DATE, temporal("(curdate())").kind());
		assertEquals(TemporalKind.DATE, temporal("curdate()").kind());
		assertEquals(TemporalKind.DATE, temporal("CURRENT_DATE").kind());
		assertEquals(TemporalKind.TIME, temporal("(curtime())").kind());
		assertEquals(TimeBasis.UTC, temporal("(utc_timestamp())").basis());
		assertEquals(EvaluationPoint.CALL, temporal("sysdate()").point());
		assertEquals(new DefaultExpression.StringLiteral("a'b"), parse("'a''b'"));
		assertEquals(new DefaultExpression.StringLiteral("NULL"), parse("'NULL'"));
		assertEquals(new DefaultExpression.NullValue(), parse("NULL"));
		assertEquals(new DefaultExpression.NumericLiteral("-5"), parse("-5"));
		assertEquals(new DefaultExpression.BitStringLiteral("1"), parse("b'1'"));
		assertInstanceOf(DefaultExpression.Unrecognized.class, parse("((1 + 2))"));
		assertInstanceOf(DefaultExpression.Unrecognized.class, parse("curdate"));
	}

	@Test
	void currentTimestampUsesTheColumnPrecision() {
		final List<String> sql = support.temporalCandidates(new Column("c").setDataType(DataType.TIMESTAMP).setLength(3L))
				.stream().map(TemporalCandidate::sql).toList();
		assertEquals(List.of("CURRENT_TIMESTAMP(3)", "(UTC_TIMESTAMP(3))", "(SYSDATE(3))"), sql);
		assertEquals("CURRENT_TIMESTAMP",
				support.temporalCandidates(new Column("c").setDataType(DataType.DATETIME)).getFirst().sql());
		assertEquals("(CURRENT_DATE)",
				support.temporalCandidates(new Column("c").setDataType(DataType.DATE)).getFirst().sql());
		for (final TemporalKind kind : List.of(TemporalKind.DATETIME, TemporalKind.DATE, TemporalKind.TIME))
			for (final TemporalCandidate candidate : support.temporalCandidates(kind))
				assertEquals(candidate.semantics(), temporal(candidate.sql()), candidate.sql());
		final Column timestamp = new Column("c").setDataType(DataType.TIMESTAMPVERSION).setDataTypeName("TIMESTAMP")
				.setLength(6L);
		assertEquals(TemporalKind.DATETIME_WITH_TIME_ZONE, support.storedKind(timestamp));
		assertEquals(List.of("CURRENT_TIMESTAMP(6)"),
				support.temporalCandidates(timestamp).stream().map(TemporalCandidate::sql).toList());
	}

	@Test
	void olderVersionsOnlyOfferCurrentTimestamp() {
		final var old = DialectResolver.getInstance().getDialect("mysql", 5, 7, 0).getDefaultExpressionSupport();
		assertEquals(List.of("CURRENT_TIMESTAMP"), old.temporalCandidates(new Column("c").setDataType(DataType.DATETIME))
				.stream().map(TemporalCandidate::sql).toList());
		assertTrue(old.temporalCandidates(new Column("c").setDataType(DataType.DATE)).isEmpty());
	}

	@Test
	void rendersLiterals() {
		final Column column = new Column("c").setDataType(DataType.BIT);
		assertEquals("b'101'", support.bitLiteral("101", column));
		assertEquals("1", support.booleanLiteral(true, new Column("c").setDataType(DataType.BOOLEAN)));
		assertEquals("'a\\\\b''c'", support.stringLiteral("a\\b'c", new Column("c").setDataType(DataType.VARCHAR)));
	}
}
