package com.sqlapp.data.db.dialect.sqlite;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpression;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionSupport;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalCandidate;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TemporalKind;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TimeBasis;
import com.sqlapp.data.schemas.Column;

class SqliteDefaultExpressionSupportTest {
	private final com.sqlapp.data.db.dialect.Dialect dialect = DialectResolver.getInstance().getDialect("SQLite", 3,
			45, 0);
	private final DefaultExpressionSupport support = dialect.getDefaultExpressionSupport();

	private static final DateTimeFormatter LOCAL = new DateTimeFormatterBuilder().appendPattern("uuuu-MM-dd HH:mm:ss")
			.optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd().toFormatter();

	@Test
	void recognizesItsForms() {
		assertInstanceOf(SqliteDefaultExpressionSupport.class, support);
		final var column = new Column("c").setDataType(DataType.TIMESTAMP);
		assertEquals(TimeBasis.UTC, ((DefaultExpression.CurrentTime) support.parse("CURRENT_TIMESTAMP", column))
				.semantics().basis());
		assertEquals(TimeBasis.SERVER_CLOCK,
				((DefaultExpression.CurrentTime) support.parse("(datetime('now','localtime'))", column)).semantics()
						.basis());
		assertEquals(new DefaultExpression.BooleanLiteral(true), support.parse("TRUE", column));
		assertInstanceOf(DefaultExpression.Unrecognized.class, support.parse("(datetime('now','+1 day'))", column));
		for (final TemporalKind kind : TemporalKind.values())
			for (final TemporalCandidate candidate : support.temporalCandidates(kind))
				assertEquals(candidate.semantics(),
						((DefaultExpression.CurrentTime) support.parse(candidate.sql(), column)).semantics(),
						candidate.sql());
	}

	/** Values produced by the candidates are read back by sqlapp's ISO-8601 handlers. */
	@Test
	void candidateValuesUseTheStoredTextFormat() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			final StringBuilder ddl = new StringBuilder("CREATE TABLE d (id INTEGER");
			int index = 0;
			final var kinds = new java.util.ArrayList<TemporalKind>();
			for (final TemporalKind kind : TemporalKind.values())
				for (final TemporalCandidate candidate : support.temporalCandidates(kind)) {
					ddl.append(", c").append(index++).append(" TEXT DEFAULT ").append(candidate.sql());
					kinds.add(kind);
				}
			statement.execute(ddl.append(")").toString());
			statement.executeUpdate("INSERT INTO d(id) VALUES (1)");
			try (var rows = statement.executeQuery("SELECT * FROM d")) {
				assertTrue(rows.next());
				for (int i = 0; i < kinds.size(); i++) {
					final String text = rows.getString("c" + i);
					switch (kinds.get(i)) {
					case DATETIME -> assertNotNull(LocalDateTime.parse(text, LOCAL), text);
					case DATE -> assertNotNull(LocalDate.parse(text), text);
					case TIME -> assertNotNull(LocalTime.parse(text), text);
					case DATETIME_WITH_TIME_ZONE -> assertNotNull(OffsetDateTime.parse(text,
							new DateTimeFormatterBuilder().append(LOCAL).appendOffset("+HH:MM", "+00:00").toFormatter()),
							text);
					default -> fail(kinds.get(i).name());
					}
					final var type = dialect.getDbDataTypes().getDbType(switch (kinds.get(i)) {
					case DATETIME -> DataType.TIMESTAMP;
					case DATE -> DataType.DATE;
					case TIME -> DataType.TIME;
					default -> DataType.TIMESTAMP_WITH_TIMEZONE;
					});
					assertNotNull(type.getJdbcTypeHandler().getObject(rows, "c" + i), text);
				}
			}
		}
	}
}
