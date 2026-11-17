/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.converter.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGInterval;

import com.sqlapp.data.db.dialect.postgres.converter.FromPGIntervalConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGIntervalConverter;

/** Driver objects only; no database connection is used. */
@Tag("known-bug")
class PGIntervalKnownBugTest {
	@Test
	void mixedSignsSurviveDriverObjectRoundTrip() {
		final PGInterval source = new PGInterval(0, 0, 1, -1, 0, 0);
		final PGInterval restored = new ToPGIntervalConverter()
				.convertObject(new FromPGIntervalConverter().convertObject(source));
		assertEquals(source.getDays(), restored.getDays());
		assertEquals(source.getHours(), restored.getHours());
	}
}
