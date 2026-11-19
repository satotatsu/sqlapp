package com.sqlapp.data.db.dialect.spanner.sql;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

import com.sqlapp.data.schemas.Sequence;

/** A captured GoogleSQL sequence counter, prior to bit reversal. */
public record SpannerSequenceState(String schema, String name, Long counter) {
	private static final int BATCH_SIZE = 64;

	public SpannerSequenceState {
		identifier(name);
		if (schema != null && !schema.isEmpty())
			identifier(schema);
		if (counter != null && counter <= 0)
			throw new IllegalArgumentException("Unexpected nonpositive Spanner sequence counter: " + counter);
	}

	/** Reads explicitly selected sequences without allocating values. */
	public static List<SpannerSequenceState> capture(final Connection connection, final List<Sequence> sequences)
			throws SQLException {
		final var inputs = new ArrayList<SpannerSequenceState>();
		final var names = new HashSet<String>();
		for (final Sequence sequence : sequences) {
			final var input = new SpannerSequenceState(sequence.getSchemaName(), sequence.getName(), null);
			if (!names.add(input.qualifiedName().toLowerCase(Locale.ROOT)))
				throw new IllegalArgumentException("Duplicate Spanner sequence: " + input.qualifiedName());
			inputs.add(input);
		}
		final var states = new ArrayList<SpannerSequenceState>();
		for (int first = 0; first < inputs.size(); first += BATCH_SIZE) {
			final var batch = inputs.subList(first, Math.min(first + BATCH_SIZE, inputs.size()));
			final var sql = new StringJoiner(", ", "SELECT ", "");
			for (final var input : batch)
				sql.add("GET_INTERNAL_SEQUENCE_STATE(SEQUENCE " + input.qualifiedName() + ")");
			try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql.toString())) {
				if (!rows.next())
					throw new SQLException("Spanner returned no sequence counter row");
				for (int index = 0; index < batch.size(); index++) {
					final long value = rows.getLong(index + 1);
					final var input = batch.get(index);
					states.add(new SpannerSequenceState(input.schema(), input.name(), rows.wasNull() ? null : value));
				}
				if (rows.next())
					throw new SQLException("Spanner returned multiple sequence counter rows");
			}
		}
		return List.copyOf(states);
	}

	/** Projects a sequence for creation in a fresh database. An unused sequence retains its configured start. */
	public Sequence forCreate(final Sequence definition, final long buffer) {
		final var identity = new SpannerSequenceState(definition.getSchemaName(), definition.getName(), null);
		if (!qualifiedName().equals(identity.qualifiedName()))
			throw new IllegalArgumentException("Spanner counter does not belong to " + identity.qualifiedName());
		if (buffer <= 0)
			throw new IllegalArgumentException("Spanner sequence restoration requires a positive buffer");
		final var result = definition.clone();
		if (counter != null)
			result.setStartValue(BigInteger.valueOf(Math.addExact(counter, buffer)));
		return result;
	}

	private String qualifiedName() {
		return Objects.toString(schema, "").isEmpty() ? identifier(name) : identifier(schema) + "." + identifier(name);
	}

	private static String identifier(final String value) {
		if (value == null || value.length() > 128 || !value.matches("[A-Za-z][A-Za-z0-9_]*"))
			throw new IllegalArgumentException("Invalid Spanner sequence identifier: " + value);
		return "`" + value + "`";
	}
}
