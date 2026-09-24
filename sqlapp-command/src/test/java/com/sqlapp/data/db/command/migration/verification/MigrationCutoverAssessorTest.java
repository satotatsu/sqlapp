/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.verification;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.migration.MigrationFreshnessCheck;

class MigrationCutoverAssessorTest {

	@Test
	void evaluatesLagAndVerificationAge() throws Exception {
		try (var source = DriverManager.getConnection("jdbc:hsqldb:mem:fresh_source", "SA", "");
				var target = DriverManager.getConnection("jdbc:hsqldb:mem:fresh_target", "SA", "")) {
			create(source, Instant.parse("2026-09-16T10:00:00Z"));
			create(target, Instant.parse("2026-09-16T09:58:00Z"));
			final var check = new MigrationFreshnessCheck("orders", null, null, "EVENTS", "UPDATED_AT", null, null,
					Map.of(), Duration.ofMinutes(5));
			final Instant now = Instant.parse("2026-09-16T10:01:00Z");

			final var ready = MigrationCutoverAssessor.assess(source, target, List.of(check),
					Instant.parse("2026-09-16T09:59:00Z"), Duration.ofMinutes(5), now);
			final var stale = MigrationCutoverAssessor.assess(source, target, List.of(check),
					Instant.parse("2026-09-16T09:00:00Z"), Duration.ofMinutes(5), now);

			assertEquals(MigrationCutoverReport.Status.READY, ready.status());
			assertEquals(Duration.ofMinutes(2), ready.freshness().getFirst().lag());
			assertEquals(MigrationCutoverReport.Status.NOT_READY_VERIFICATION_STALE, stale.status());
		}
	}

	private static void create(final java.sql.Connection connection, final Instant value) throws Exception {
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE EVENTS(UPDATED_AT TIMESTAMP)");
		}
		try (var statement = connection.prepareStatement("INSERT INTO EVENTS VALUES (?)")) {
			statement.setTimestamp(1, Timestamp.from(value));
			statement.executeUpdate();
		}
	}
}
