package com.sqlapp.data.db.dialect.sqlserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpression;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens.Replacement;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalTranslator;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration.Outcome;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalCandidate;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.EvaluationPoint;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TemporalKind;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TimeBasis;
import com.sqlapp.data.schemas.Column;

class SqlServerDefaultExpressionSupportTest {

	private final SqlServerDefaultExpressionSupport support = new SqlServerDefaultExpressionSupport(
			DialectHolder.defaultDialect2022);

	private DefaultExpression parse(final String sql, final DataType type) {
		return support.parse(sql, new Column("c").setDataType(type));
	}

	private TemporalSemantics temporal(final String sql) {
		return assertInstanceOf(DefaultExpression.CurrentTime.class, parse(sql, DataType.DATETIME)).semantics();
	}

	/**
	 * Forms as stored by SQL Server 2022 after the reader removes enclosing
	 * parentheses.
	 */
	@Test
	void recognizesStoredForms() {
		assertEquals(new TemporalSemantics(EvaluationPoint.STATEMENT_START, TimeBasis.SERVER_CLOCK,
				TemporalKind.DATETIME, 3), temporal("getdate()"));
		assertEquals(7, temporal("sysdatetime()").precision());
		assertEquals(TimeBasis.UTC, temporal("getutcdate()").basis());
		assertEquals(TimeBasis.UTC, temporal("sysutcdatetime()").basis());
		final var offset = assertInstanceOf(DefaultExpression.CurrentTime.class,
				parse("sysdatetimeoffset()", DataType.TIMESTAMP_WITH_TIMEZONE)).semantics();
		assertEquals(TemporalKind.DATETIME_WITH_TIME_ZONE, offset.kind());
		assertEquals(TimeBasis.INSTANT, offset.basis());
		// Stored without its offset, the value is the server's local time.
		final var local = temporal("sysdatetimeoffset()");
		assertEquals(TemporalKind.DATETIME, local.kind());
		assertEquals(TimeBasis.SERVER_CLOCK, local.basis());
		assertEquals(TemporalKind.DATE, temporal("CONVERT([date],getdate())").kind());
		assertEquals(TemporalKind.DATE, temporal("CAST(SYSDATETIME() AS date)").kind());
		final var time = temporal("CONVERT([time],sysdatetime())");
		assertEquals(TemporalKind.TIME, time.kind());
		assertEquals(7, time.precision());
		assertEquals(TimeBasis.SERVER_CLOCK, temporal("CURRENT_TIMESTAMP").basis());

		assertEquals(new DefaultExpression.NumericLiteral("1"), parse("((1))", DataType.BOOLEAN));
		assertEquals(new DefaultExpression.NumericLiteral("-5"), parse("(-5)", DataType.INT));
		assertEquals(new DefaultExpression.NumericLiteral("1.50"), parse("(1.50)", DataType.DECIMAL));
		assertEquals(new DefaultExpression.StringLiteral("NULL"), parse("'NULL'", DataType.VARCHAR));
		assertEquals(new DefaultExpression.NullValue(), parse("NULL", DataType.INT));
		assertEquals(new DefaultExpression.StringLiteral("あ'b"), parse("N'あ''b'", DataType.NVARCHAR));
		assertInstanceOf(DefaultExpression.Unrecognized.class, parse("(1)+(2)", DataType.INT));
		assertInstanceOf(DefaultExpression.Unrecognized.class, parse("newid()", DataType.UUID));
		assertInstanceOf(DefaultExpression.Unrecognized.class,
				parse("CONVERT([varchar](8),getdate(),(112))", DataType.VARCHAR));
	}

	@Test
	void offersVersionDependentCandidates() {
		final List<String> datetime = support.temporalCandidates(new Column("c").setDataType(DataType.TIMESTAMP))
				.stream().map(TemporalCandidate::sql).toList();
		assertEquals(List.of("SYSDATETIME()", "SYSUTCDATETIME()"), datetime);
		assertEquals("SYSDATETIMEOFFSET()", support
				.temporalCandidates(new Column("c").setDataType(DataType.TIMESTAMP_WITH_TIMEZONE)).getFirst().sql());
		final var old = new SqlServerDefaultExpressionSupport(DialectHolder.defaultDialect2005);
		assertEquals(List.of("CURRENT_TIMESTAMP", "GETUTCDATE()"),
				old.temporalCandidates(new Column("c").setDataType(DataType.DATETIME)).stream()
						.map(TemporalCandidate::sql).toList());
		assertTrue(old.temporalCandidates(new Column("c").setDataType(DataType.DATE)).isEmpty());
	}

	@Test
	void rendersLiteralsForTheColumn() {
		assertEquals("N'a''b'", support.stringLiteral("a'b", new Column("c").setDataType(DataType.NVARCHAR)));
		assertEquals("'a''b'", support.stringLiteral("a'b", new Column("c").setDataType(DataType.VARCHAR)));
		assertEquals("1", support.booleanLiteral(true, new Column("c").setDataType(DataType.BOOLEAN)));
	}

	@Test
	void preservesWithinSqlServerUnlessTheTargetLacksTheFunction() {
		final Column source = new Column("c").setDataType(DataType.DATETIME).setDefaultValue("sysdatetime()");
		final var same = DefaultValueMigration.convert(source, DialectHolder.defaultDialect2016, source.clone(),
				DialectHolder.defaultDialect2022, null, null);
		assertEquals(Outcome.PRESERVED, same.outcome());
		final var older = DefaultValueMigration.convert(source, DialectHolder.defaultDialect2016, source.clone(),
				DialectHolder.defaultDialect2005, null, null);
		assertEquals(Outcome.CONVERTED_APPROXIMATE, older.outcome());
		assertEquals("CURRENT_TIMESTAMP", older.targetExpression());
		assertFalse(older.isBlocking());
	}

	/**
	 * A recognized call inside a longer body is replaced without touching the rest.
	 */
	@Test
	void recognizesARangeInsideABodyAndReplacesOnlyThatRange() {
		final String body = "UPDATE t SET modified = getdate() /* getdate() */ WHERE id = 1 -- getdate()";
		final var tokens = DefaultExpressionTokens.tokenize(body);
		final var call = tokens.subList(5, 8);
		final var semantics = assertInstanceOf(DefaultExpression.CurrentTime.class, support.recognize(body, call, null))
				.semantics();
		assertEquals(TimeBasis.SERVER_CLOCK, semantics.basis());
		assertInstanceOf(DefaultExpression.Unrecognized.class, support.recognize(body, tokens.subList(0, 3), null));
		final var selection = TemporalTranslator.select(semantics.withPrecision(7), null, null, support).orElseThrow();
		assertEquals("SYSDATETIME()", selection.candidate().sql());
		assertTrue(selection.isExact());
		assertEquals("UPDATE t SET modified = SYSDATETIME() /* getdate() */ WHERE id = 1 -- getdate()",
				DefaultExpressionTokens.replace(body, List.of(Replacement.of(call, selection.candidate().sql()))));
	}
}
