/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.metadata;

import java.math.BigDecimal;

/**
 * Converts a database's "last issued" sequence or identity value to the
 * canonical model value: the next value to be issued.
 * <p>
 * The model's {@code lastValue} and {@code identityLastValue} hold the next
 * value a recreated object should hand out (a {@code START WITH}/{@code RESTART
 * WITH} value). Databases that expose the next value or a cache allocation
 * boundary are stored as is; databases that expose the last issued value are
 * converted here. {@code null} means the value is unknown or the object is
 * unused, so the start value applies.
 */
public final class SequenceNextValues {
	private SequenceNextValues() {
	}

	/**
	 * Returns the value after {@code issued}.
	 *
	 * @param issued    last issued value, or null when unused or unknown
	 * @param increment increment; null means 1
	 * @param min       minimum value, or null when unknown
	 * @param max       maximum value, or null when unknown
	 * @param cycle     whether the sequence wraps at its bounds
	 * @return the next value, or null when {@code issued} is null. Without CYCLE an
	 *         exhausted sequence returns a value beyond its bound rather than a
	 *         silently clamped one.
	 */
	public static BigDecimal after(BigDecimal issued, BigDecimal increment, BigDecimal min, BigDecimal max,
			boolean cycle) {
		if (issued == null) {
			return null;
		}
		BigDecimal step = increment == null ? BigDecimal.ONE : increment;
		if (step.signum() == 0) {
			throw new IllegalArgumentException("Sequence increment must not be zero");
		}
		BigDecimal next = issued.add(step);
		if (cycle) {
			if (step.signum() > 0 && max != null && next.compareTo(max) > 0 && min != null) {
				return min;
			}
			if (step.signum() < 0 && min != null && next.compareTo(min) < 0 && max != null) {
				return max;
			}
		}
		return next;
	}

	/**
	 * Identity form of {@link #after(BigDecimal, BigDecimal, BigDecimal, BigDecimal, boolean)}
	 * for columns, which do not cycle.
	 */
	public static Long after(Long issued, Long increment) {
		if (issued == null) {
			return null;
		}
		long step = increment == null ? 1L : increment.longValue();
		if (step == 0) {
			throw new IllegalArgumentException("Identity increment must not be zero");
		}
		return Math.addExact(issued.longValue(), step);
	}
}
