/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.SimpleAlterSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Sequence;

/** Changes the skip range of an existing GoogleSQL sequence. */
public class SpannerAlterSequenceFactory extends SimpleAlterSqlFactory<Sequence, SpannerSqlBuilder> {
	public static final String SEQUENCE_DDL_STYLE = "SEQUENCE_DDL_STYLE";
	public static final String RESTART_COUNTER_WITH = "RESTART_COUNTER_WITH";

	@Override
	public List<SqlOperation> createSql(final Sequence sequence) {
		throw new UnsupportedOperationException("Spanner ALTER SEQUENCE requires an original and target sequence");
	}

	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference difference) {
		final var original = difference.getOriginal(Sequence.class);
		final var target = difference.getTarget(Sequence.class);
		final var changes = difference.toDifference().getChangedProperties(getDialect());
		for (final String property : changes.keySet()) {
			if (!SpannerCreateSequenceFactory.SKIP_RANGE_MIN.equalsIgnoreCase(property)
					&& !SpannerCreateSequenceFactory.SKIP_RANGE_MAX.equalsIgnoreCase(property)
					&& !SEQUENCE_DDL_STYLE.equalsIgnoreCase(property)
					&& !RESTART_COUNTER_WITH.equalsIgnoreCase(property)) {
				throw new UnsupportedOperationException("Spanner sequence change requires a separate migration: " + property);
			}
		}
		final String originalStyle = original.getSpecifics().get(SEQUENCE_DDL_STYLE);
		final String targetStyle = target.getSpecifics().get(SEQUENCE_DDL_STYLE);
		if (originalStyle != null && targetStyle != null && !originalStyle.equalsIgnoreCase(targetStyle))
			throw new IllegalArgumentException("Spanner sequence ALTER requires the original DDL style: " + target.getName());
		final boolean rangeChanged = changes.containsKey(SpannerCreateSequenceFactory.SKIP_RANGE_MIN)
				|| changes.containsKey(SpannerCreateSequenceFactory.SKIP_RANGE_MAX);
		final boolean restart = changes.containsKey(RESTART_COUNTER_WITH);
		if (!rangeChanged && !restart)
			return List.of();
		final var builder = createSqlBuilder(getDialect());
		addChanges(target, originalStyle != null ? originalStyle : targetStyle, rangeChanged, restart, builder);
		return List.of(createOperation(builder.toString(), SqlType.ALTER, original, target));
	}

	private void addChanges(final Sequence sequence, final String style, final boolean rangeChanged,
			final boolean restart, final SpannerSqlBuilder builder) {
		if (sequence.getName() == null || sequence.getName().isBlank())
			throw new IllegalArgumentException("Spanner ALTER SEQUENCE requires a name");
		if (!"CLAUSES".equalsIgnoreCase(style) && !"OPTIONS".equalsIgnoreCase(style))
			throw new IllegalArgumentException("Set Spanner SEQUENCE_DDL_STYLE to CLAUSES or OPTIONS for "
					+ sequence.getName() + " based on how the existing sequence was created");
		final var range = SpannerCreateSequenceFactory.skipRange(sequence);
		final Long counter = restart ? SpannerCounterRestart.value(sequence.getSpecifics().get(RESTART_COUNTER_WITH),
				sequence.getName()) : null;
		builder.alter().sequence().name(sequence, getOptions().isDecorateSchemaName()).space();
		if ("OPTIONS".equalsIgnoreCase(style)) {
			builder._add("SET OPTIONS (");
			if (rangeChanged)
				builder._add("skip_range_min = ")._add(range.minimum() == null ? "NULL" : range.minimum())
						._add(", skip_range_max = ")._add(range.maximum() == null ? "NULL" : range.maximum());
			if (counter != null)
				builder._add(rangeChanged ? ", " : "")._add("start_with_counter = ")._add(counter);
			builder._add(")");
		} else {
			if (rangeChanged) {
				if (range.minimum() == null)
					builder._add("NO SKIP RANGE");
				else
					builder._add("SKIP RANGE ")._add(range.minimum())._add(", ")._add(range.maximum());
			}
			if (counter != null)
				builder._add(rangeChanged ? " " : "")._add("RESTART COUNTER WITH ")._add(counter);
		}
	}
}
