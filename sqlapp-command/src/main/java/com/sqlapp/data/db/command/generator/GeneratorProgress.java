package com.sqlapp.data.db.command.generator;

import java.util.Locale;
import java.util.function.LongSupplier;

/** Bounded progress logging for exact and unknown source counts. */
final class GeneratorProgress {
	private final long total;
	private final LongSupplier clock;
	private final long start;
	private long lastLog;
	private long lastPercentage = -1;
	private boolean logged;

	GeneratorProgress(long total, LongSupplier clock) {
		this.total = total;
		this.clock = clock;
		this.start = clock.getAsLong();
	}

	String update(long processed) {
		long now = clock.getAsLong();
		long percentage = total > 0
				? processed >= total ? 100 : (long) Math.min(99, processed * 100.0 / total) : -1;
		if (logged && (total > 0 ? percentage == lastPercentage : now - lastLog < 1_000_000_000L)) {
			return null;
		}
		logged = true;
		lastPercentage = percentage;
		lastLog = now;
		long elapsed = Math.max(0, now - start);
		double rate = elapsed == 0 ? 0 : processed * 1_000_000_000.0 / elapsed;
		String rows = total > 0 ? percentage + "% update completed.[" + processed + "/" + total + "]"
				: "Rows processed=[" + processed + "]. total=[unknown]";
		return rows + String.format(Locale.ROOT, ". elapsedMs=[%d]. rowsPerSecond=[%.1f]", elapsed / 1_000_000, rate);
	}
}
