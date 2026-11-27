package com.sqlapp.data.db.dialect.defaultvalue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.ColumnMigration;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.MigrationDiagnostic;
import com.sqlapp.data.db.dialect.MigrationDiagnostic.Severity;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens.Token;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration.Difference;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration.Outcome;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration.Verification;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.EvaluationPoint;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TemporalKind;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TimeBasis;
import com.sqlapp.data.schemas.Column;

class DefaultValueMigrationTest {

	/** Source product: now_stmt() is statement start on the server clock, 7 digits. */
	private static final Dialect SOURCE = new TestDialect("Source", Map.of("now_stmt",
			new TemporalSemantics(EvaluationPoint.STATEMENT_START, TimeBasis.SERVER_CLOCK, TemporalKind.DATETIME, 7),
			"now_utc", new TemporalSemantics(EvaluationPoint.STATEMENT_START, TimeBasis.UTC, TemporalKind.DATETIME, 3),
			"now_odd", new TemporalSemantics(EvaluationPoint.UNKNOWN, TimeBasis.UNKNOWN, TemporalKind.DATETIME, null)),
			List.of());

	private static final Dialect TARGET = new TestDialect("Target", Map.of(), List.of(
			new TemporalCandidate("TX_LOCAL", sem(EvaluationPoint.TRANSACTION_START, TimeBasis.SESSION_TIME_ZONE,
					TemporalKind.DATETIME, 6)),
			new TemporalCandidate("ST_LOCAL", sem(EvaluationPoint.STATEMENT_START, TimeBasis.SESSION_TIME_ZONE,
					TemporalKind.DATETIME, 6)),
			new TemporalCandidate("ST_UTC", sem(EvaluationPoint.STATEMENT_START, TimeBasis.UTC, TemporalKind.DATETIME, 6)),
			new TemporalCandidate("TODAY", sem(EvaluationPoint.TRANSACTION_START, TimeBasis.SESSION_TIME_ZONE,
					TemporalKind.DATE, 0))));

	private static TemporalSemantics sem(final EvaluationPoint point, final TimeBasis basis, final TemporalKind kind,
			final Integer precision) {
		return new TemporalSemantics(point, basis, kind, precision);
	}

	private static Column column(final DataType type, final String defaultValue) {
		return new Column("c").setDataType(type).setDefaultValue(defaultValue);
	}

	private static DefaultValueMigration.Result convert(final Column source, final Column target) {
		return DefaultValueMigration.convert(source, SOURCE, target, TARGET, DefaultValueMigration.Options.DEFAULT, null);
	}

	@Test
	void distinguishesLiterals() {
		final Column target = column(DataType.VARCHAR, null);
		assertEquals(Outcome.NO_DEFAULT, convert(column(DataType.VARCHAR, null), target).outcome());
		assertEquals("NULL", convert(column(DataType.VARCHAR, "NULL"), target).targetExpression());
		assertEquals("'NULL'", convert(column(DataType.VARCHAR, "'NULL'"), target).targetExpression());
		assertEquals("'it''s'", convert(column(DataType.VARCHAR, "('it''s')"), target).targetExpression());
		assertEquals("-5", convert(column(DataType.INT, "((-5))"), column(DataType.INT, null)).targetExpression());
		final var bool = convert(column(DataType.BOOLEAN, "((1))"), column(DataType.BOOLEAN, null));
		assertEquals("TRUE", bool.targetExpression());
		assertEquals(Outcome.CONVERTED_EXACT, bool.outcome());
		final var notBoolean = convert(column(DataType.VARCHAR, "'Y'"), column(DataType.BOOLEAN, null));
		assertEquals("'Y'", notBoolean.targetExpression());
		assertEquals("DEFAULT_LITERAL_TARGET_PARSED", notBoolean.diagnostics().getFirst().code());
		assertEquals(Outcome.REQUIRES_ACTION,
				convert(column(DataType.INT, "((2))"), column(DataType.BOOLEAN, null)).outcome());
	}

	@Test
	void selectsDeterministicallyAndReportsDifferences() {
		final var result = convert(column(DataType.TIMESTAMP, "now_stmt()").setLength(7L),
				column(DataType.TIMESTAMP, null).setLength(6L));
		// No candidate uses the server clock: the statement-start candidate in the session zone wins.
		assertEquals("ST_LOCAL", result.targetExpression());
		assertEquals(Outcome.CONVERTED_APPROXIMATE, result.outcome());
		assertEquals(List.of(Difference.TIME_BASIS, Difference.PRECISION), result.differences());
		assertEquals(List.of(Severity.WARNING, Severity.INFO),
				result.diagnostics().stream().map(MigrationDiagnostic::severity).toList());
		assertEquals(Verification.STATICALLY_CHECKED, result.verification());

		final var utc = convert(column(DataType.TIMESTAMP, "now_utc()").setLength(3L),
				column(DataType.TIMESTAMP, null).setLength(6L));
		assertEquals("ST_UTC", utc.targetExpression());
		assertEquals(Outcome.CONVERTED_EXACT, utc.outcome());
		assertTrue(utc.diagnostics().isEmpty());
	}

	@Test
	void reportsColumnCoercionSeparately() {
		final var result = convert(column(DataType.TIMESTAMP, "now_utc()"), column(DataType.DATE, null));
		assertEquals("TODAY", result.targetExpression());
		assertTrue(result.differences().contains(Difference.COLUMN_TIME_REMOVED));
		assertTrue(result.differences().contains(Difference.TIME_BASIS));
		// A date-only source column already stored only the date: no coercion difference.
		final var date = convert(column(DataType.DATE, "now_utc()"), column(DataType.DATE, null));
		assertTrue(!date.differences().contains(Difference.COLUMN_TIME_REMOVED));
	}

	@Test
	void unknownSemanticsAreNeverExact() {
		final var result = convert(column(DataType.TIMESTAMP, "now_odd()"), column(DataType.TIMESTAMP, null));
		assertEquals(Outcome.CONVERTED_APPROXIMATE, result.outcome());
		assertEquals(List.of(Difference.UNKNOWN_SEMANTICS), result.differences());
	}

	@Test
	void strictModeMakesDifferencesUnresolved() {
		final var result = DefaultValueMigration.convert(column(DataType.TIMESTAMP, "now_stmt()"), SOURCE,
				column(DataType.TIMESTAMP, null), TARGET, DefaultValueMigration.Options.STRICT, null);
		assertEquals("ST_LOCAL", result.targetExpression());
		assertTrue(result.isBlocking());
		assertTrue(result.diagnostics().stream().allMatch(d -> d.severity() == Severity.UNRESOLVED));
	}

	@Test
	void unrecognizedAndUnresolvableDefaultsRequireAction() {
		final var unknown = convert(column(DataType.INT, "(1)+(2)"), column(DataType.INT, null));
		assertEquals(Outcome.REQUIRES_ACTION, unknown.outcome());
		assertNull(unknown.targetExpression());
		assertEquals("DEFAULT_UNRECOGNIZED", unknown.diagnostics().getFirst().code());
		final var noDialect = DefaultValueMigration.convert(column(DataType.INT, "0"), null, column(DataType.INT, null),
				TARGET, null, null);
		assertEquals("SOURCE_DIALECT_UNKNOWN", noDialect.diagnostics().getFirst().code());
		final var noCandidate = convert(column(DataType.TIME, "now_utc()"), column(DataType.TIME, null));
		assertEquals("DEFAULT_NO_CANDIDATE", noCandidate.diagnostics().getFirst().code());
	}

	@Test
	void sameProductPreservesTheExpression() {
		final var recognized = DefaultValueMigration.convert(column(DataType.TIMESTAMP, "now_stmt()"), SOURCE,
				column(DataType.TIMESTAMP, null), SOURCE, null, null);
		assertEquals(Outcome.PRESERVED, recognized.outcome());
		assertEquals("now_stmt()", recognized.targetExpression());
		assertEquals(Verification.STATICALLY_CHECKED, recognized.verification());
		final var unknown = DefaultValueMigration.convert(column(DataType.INT, "dbo.f()"), SOURCE,
				column(DataType.INT, null), SOURCE, null, null);
		assertEquals(Outcome.PRESERVED, unknown.outcome());
		assertEquals(Verification.UNCHECKED, unknown.verification());
		assertEquals(Severity.INFO, unknown.diagnostics().getFirst().severity());
	}

	@Test
	void overridesTakePrecedence() {
		final var replaced = DefaultValueMigration.convert(column(DataType.INT, "(1)+(2)"), SOURCE,
				column(DataType.INT, null), TARGET, null, new DefaultValueMigration.Override.Replace("3"));
		assertEquals(Outcome.REPLACED, replaced.outcome());
		assertEquals("3", replaced.targetExpression());
		final var removed = DefaultValueMigration.convert(column(DataType.INT, "(1)+(2)"), SOURCE,
				column(DataType.INT, null), TARGET, null, new DefaultValueMigration.Override.Remove());
		assertEquals(Outcome.REMOVED, removed.outcome());
		assertNull(removed.targetExpression());
		assertTrue(!removed.isBlocking());
		assertThrows(IllegalArgumentException.class, () -> new DefaultValueMigration.Override.Replace(" "));
	}

	@Test
	void columnMigrationKeepsSourceAndBlocksOnlyUnresolved() {
		final Column source = column(DataType.TIMESTAMP, "now_stmt()").setOnUpdate("now_stmt()");
		final var result = ColumnMigration.convert(source, SOURCE, TARGET);
		assertEquals("now_stmt()", source.getDefaultValue());
		assertEquals("ST_LOCAL", result.candidate().getDefaultValue());
		assertNull(result.candidate().getOnUpdate());
		assertSame(Outcome.CONVERTED_APPROXIMATE, result.defaultValue().outcome());
		assertEquals("ON_UPDATE_UNSUPPORTED", result.blockingDiagnostics().getFirst().code());
		assertThrows(IllegalStateException.class, result::requireComplete);

		final var warnings = ColumnMigration.convert(column(DataType.TIMESTAMP, "now_stmt()"), SOURCE, TARGET);
		assertEquals("ST_LOCAL", warnings.requireComplete().getDefaultValue());
		assertTrue(warnings.issues().size() >= 1);

		final var unresolved = ColumnMigration.convert(column(DataType.INT, "(1)+(2)"), SOURCE, TARGET);
		assertNull(unresolved.candidate().getDefaultValue());
		assertThrows(IllegalStateException.class, unresolved::requireComplete);

		final var legacy = new ColumnMigration.Result(new Column("x"), List.of("decide"));
		assertEquals(MigrationDiagnostic.UNCLASSIFIED, legacy.diagnostics().getFirst().code());
		assertThrows(IllegalStateException.class, legacy::requireComplete);
	}

	private static final class TestDialect extends Dialect {
		private static final long serialVersionUID = 1L;
		private final String product;
		private final Map<String, TemporalSemantics> functions;
		private final List<TemporalCandidate> candidates;

		TestDialect(final String product, final Map<String, TemporalSemantics> functions,
				final List<TemporalCandidate> candidates) {
			super(() -> null);
			this.product = product;
			this.functions = functions;
			this.candidates = candidates;
		}

		@Override
		public String getProductName() {
			return product;
		}

		@Override
		public DefaultExpressionSupport getDefaultExpressionSupport() {
			return new StandardDefaultExpressionSupport(this) {
				@Override
				protected DefaultExpression parseExpression(final List<Token> tokens, final com.sqlapp.data.schemas.Column column) {
					if (tokens.size() == 3 && tokens.get(1).isSymbol("(") && tokens.get(2).isSymbol(")")) {
						final TemporalSemantics semantics = functions.get(tokens.getFirst().lower());
						return semantics == null ? null : new DefaultExpression.CurrentTime(semantics);
					}
					return null;
				}

				@Override
				public List<TemporalCandidate> temporalCandidates(final com.sqlapp.data.schemas.Column column) {
					return candidates;
				}
			};
		}
	}
}
