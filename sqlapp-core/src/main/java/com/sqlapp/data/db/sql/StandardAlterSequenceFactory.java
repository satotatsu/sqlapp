/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.sql;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.State;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * {@code ALTER SEQUENCE} for databases that follow the SQL standard clauses.
 * <p>
 * A modified sequence is altered in place: increment, bounds, cycle, cache and
 * the next value ({@code lastValue}, applied as {@code RESTART WITH}). Any
 * other changed property fails with the property names instead of being
 * dropped, so a difference is never silently lost. Dialects override the clause
 * methods for their own spelling, or {@link #supportsRestart()} when the next
 * value cannot be changed in place.
 */
public class StandardAlterSequenceFactory<S extends AbstractSqlBuilder<?>> extends AbstractAlterSequenceFactory<S> {

	private static final Set<String> ALTERABLE = Set.of(SchemaProperties.LAST_VALUE.getLabel(),
			SchemaProperties.INCREMENT_BY.getLabel(), SchemaProperties.MIN_VALUE.getLabel(),
			SchemaProperties.MAX_VALUE.getLabel(), SchemaProperties.CYCLE.getLabel(), SchemaProperties.CACHE.getLabel(),
			SchemaProperties.CACHE_SIZE.getLabel());

	private static final String REMARKS = SchemaProperties.REMARKS.getLabel();

	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference difference) {
		if (difference.getState() != State.Modified || !(difference.getOriginal() instanceof Sequence)
				|| !(difference.getTarget() instanceof Sequence target)) {
			return super.createDiffSql(difference);
		}
		final Set<String> changed = difference.getChangedProperties(getDialect()).keySet();
		final Set<String> unsupported = new TreeSet<>(changed);
		unsupported.removeAll(ALTERABLE);
		unsupported.remove(REMARKS);
		if (!isCacheAlterable()) {
			unsupported.remove(SchemaProperties.CACHE.getLabel());
			unsupported.remove(SchemaProperties.CACHE_SIZE.getLabel());
		}
		if (!unsupported.isEmpty()) {
			throw new IllegalArgumentException("Sequence " + target.getName() + " cannot be altered in place for "
					+ unsupported + "; drop and recreate it explicitly");
		}
		final List<SqlOperation> result = new ArrayList<>();
		final S builder = createSqlBuilder(getDialect());
		addAlterSequence(target, builder);
		final int start = builder.toString().length();
		if (changed.contains(SchemaProperties.INCREMENT_BY.getLabel())) {
			requireValue(target, target.getIncrementBy(), "incrementBy");
			addIncrementBy(target, builder);
		}
		if (changed.contains(SchemaProperties.MIN_VALUE.getLabel())) {
			addMinValue(target, builder);
		}
		if (changed.contains(SchemaProperties.MAX_VALUE.getLabel())) {
			addMaxValue(target, builder);
		}
		if (changed.contains(SchemaProperties.CYCLE.getLabel())) {
			addCycle(target, builder);
		}
		if (isCacheAlterable() && (changed.contains(SchemaProperties.CACHE.getLabel())
				|| changed.contains(SchemaProperties.CACHE_SIZE.getLabel()))) {
			addCache(target, builder);
		}
		if (changed.contains(SchemaProperties.LAST_VALUE.getLabel())) {
			addRestart(target, requireValue(target, target.getLastValue(), "lastValue"), builder);
		}
		if (builder.toString().length() > start) {
			addSql(result, builder, SqlType.ALTER, target);
		}
		if (changed.contains(REMARKS)) {
			addRemarks(target, result);
		}
		return result;
	}

	/**
	 * Restarts the sequence at its next value ({@code lastValue}).
	 */
	@Override
	public List<SqlOperation> createSql(final Sequence sequence) {
		final List<SqlOperation> result = new ArrayList<>();
		final S builder = createSqlBuilder(getDialect());
		addCreateObject(sequence, builder);
		addSql(result, builder, SqlType.ALTER, sequence);
		return result;
	}

	@Override
	protected void addCreateObject(final Sequence sequence, final S builder) {
		addAlterSequence(sequence, builder);
		addRestart(sequence, requireValue(sequence, sequence.getLastValue(), "lastValue"), builder);
	}

	/**
	 * @return whether the next value can be changed in place. When false a changed
	 *         {@code lastValue} fails and CREATE does not add a restart.
	 */
	public boolean supportsRestart() {
		return true;
	}

	/**
	 * @return whether the database has a CACHE clause. When false cache changes are
	 *         ignored, matching CREATE.
	 */
	protected boolean isCacheAlterable() {
		return true;
	}

	protected void addAlterSequence(final Sequence sequence, final S builder) {
		builder.alter().sequence().space().name(sequence, getOptions().isDecorateSchemaName());
	}

	protected void addIncrementBy(final Sequence sequence, final S builder) {
		builder.space()._add("INCREMENT BY ")._add(sequence.getIncrementBy());
	}

	protected void addMinValue(final Sequence sequence, final S builder) {
		if (sequence.getMinValue() == null) {
			builder.space()._add("NO MINVALUE");
		} else {
			builder.space()._add("MINVALUE ")._add(sequence.getMinValue());
		}
	}

	protected void addMaxValue(final Sequence sequence, final S builder) {
		if (sequence.getMaxValue() == null) {
			builder.space()._add("NO MAXVALUE");
		} else {
			builder.space()._add("MAXVALUE ")._add(sequence.getMaxValue());
		}
	}

	protected void addCycle(final Sequence sequence, final S builder) {
		builder.space()._add(sequence.isCycle() ? "CYCLE" : "NO CYCLE");
	}

	protected void addCache(final Sequence sequence, final S builder) {
		final Integer size = sequence.getCacheSize();
		if (!sequence.isCache() || size == null || size <= 1) {
			builder.space()._add("NO CACHE");
		} else {
			builder.space()._add("CACHE ")._add(size);
		}
	}

	protected void addRestart(final Sequence sequence, final BigInteger next, final S builder) {
		if (!supportsRestart()) {
			throw new IllegalArgumentException("The next value of sequence " + sequence.getName()
					+ " cannot be changed in place on " + getDialect() + "; recreate it with START WITH " + next);
		}
		builder.space()._add("RESTART WITH ")._add(next);
	}

	/**
	 * Applies changed remarks through the registered SET_COMMENT factory.
	 */
	protected void addRemarks(final Sequence sequence, final List<SqlOperation> result) {
		final SqlFactory<Sequence> factory = getSqlFactoryRegistry().getSqlFactory(sequence, SqlType.SET_COMMENT);
		if (factory == null || factory instanceof EmptySqlFactory) {
			throw new IllegalArgumentException("Sequence " + sequence.getName()
					+ " remarks cannot be altered: no SET_COMMENT factory for " + getDialect());
		}
		result.addAll(factory.createSql(sequence));
	}

	private static <T> T requireValue(final Sequence sequence, final T value, final String property) {
		if (value == null) {
			throw new IllegalArgumentException(
					"Sequence " + sequence.getName() + " " + property + " cannot be cleared by ALTER");
		}
		return value;
	}
}
