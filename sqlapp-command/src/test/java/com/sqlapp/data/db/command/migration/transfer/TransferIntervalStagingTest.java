/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransferIntervalStagingTest {
	@TempDir Path directory;
	@ParameterizedTest @ValueSource(strings = {"KEYSET", "FULLSCAN", "COMMIT_TRUE", "COMMIT_FALSE", "FAIL_INSERT", "RENAME", "MISSING", "MULTI"})
	void replaysCapturedIntervalsThroughStagingAndRetainedComparison(String mode) throws Exception {
		var from = new JdbcDataSource(); from.setURL("jdbc:h2:mem:" + UUID.randomUUID()); from.setUser("sa");
		var to = new JDBCDataSource(); to.setUrl("jdbc:hsqldb:mem:" + UUID.randomUUID()); to.setUser("SA");
		try (var source = from.getConnection(); var target = to.getConnection(); var a = source.createStatement(); var b = target.createStatement()) {
			a.execute("CREATE TABLE ITEMS(ID INT" + (mode.equals("FULLSCAN") ? "" : " PRIMARY KEY") + ",D INTERVAL DAY(9) TO SECOND(9))");
			a.execute("INSERT INTO ITEMS VALUES(1,INTERVAL '23:00:00.123456' HOUR TO SECOND(9))");
			if (mode.equals("FULLSCAN")) a.execute("INSERT INTO ITEMS SELECT * FROM ITEMS");
			if (!mode.equals("MISSING")) {
				b.execute("CREATE TABLE ITEMS(ID INT" + (mode.equals("FULLSCAN") ? "" : " PRIMARY KEY") + ","
					+ (mode.equals("RENAME") ? "ELAPSED" : "D") + " INTERVAL DAY(9) TO SECOND(6)"
					+ (mode.equals("FAIL_INSERT") ? " CHECK(D < INTERVAL '22' HOUR)" : "") + ")");
				b.execute("INSERT INTO ITEMS VALUES(99,INTERVAL '1' HOUR)");
			}
			if (mode.equals("MULTI")) {
				a.execute("CREATE TABLE SECOND_ITEMS AS SELECT * FROM ITEMS");
				b.execute("CREATE TABLE SECOND_ITEMS(ID INT,D INTERVAL DAY(9) TO SECOND(6))");
				b.execute("INSERT INTO SECOND_ITEMS VALUES(99,INTERVAL '1' HOUR)");
			}
			var job = Files.writeString(directory.resolve("job.yaml"), "formatVersion: 1\nsource: from\ntarget: to\n"
					+ "sourceSchema: PUBLIC\ntargetSchema: PUBLIC\ntables: [ITEMS]\nschemaMode: REQUIRE_EXISTING\n"
					+ "targetPreparation: STAGED_REPLACE\ninsertMethod: JDBC_BATCH\n");
			if (mode.equals("MISSING")) Files.writeString(job, Files.readString(job).replace("REQUIRE_EXISTING", "CREATE_MISSING"));
			if (mode.equals("MULTI")) Files.writeString(job, Files.readString(job).replace("[ITEMS]", "[ITEMS, SECOND_ITEMS]"));
			if (mode.equals("RENAME")) {
				new TransferNameMappingIO().write(directory.resolve("names.yaml"), new TransferNameMappingIO().format(
						new TransferNameMappingIO.Configuration(1, Map.of("ITEMS", new TransferNameMappingIO.TableMapping(null,
								Map.of("D", new TransferNameMappingIO.ColumnMapping("ELAPSED", null, null)))))), null);
				Files.writeString(job, "mappingFile: names.yaml\n", StandardOpenOption.APPEND);
			}
			var service = new TransferJobExecutionService(); var bindings = Map.of("from", from, "to", to);
			var review = service.review(job, bindings, directory);
			assertTrue(Files.readString(review.prepared().sourceSchemaFile()).contains(TransferIntervalProjection.SNAPSHOT));
			if (!mode.equals("MISSING")) assertTrue(review.prepared().warnings().stream().anyMatch(text -> text.contains("INTERVAL_PRECISION_MAPPING")));
			if (mode.startsWith("COMMIT")) {
				boolean committed = mode.equals("COMMIT_TRUE");
				var failure = assertThrows(TransferJobExecutionService.ExecutionException.class, () -> service.execute(review,
						Map.of("from", from, "to", TransferStagedReplacementTest.lostCommitReply(to, committed))));
				assertEquals(TransferStagedReplacement.State.COMMIT_UNCERTAIN, failure.outcome().staging().state());
				new TransferJobReportIO().writeReview(review);
				var report = new TransferJobReportIO().writeOutcomeSnapshot(review, failure.outcome());
				a.execute("DROP TABLE ITEMS"); // Saved comparison must not consult source data.
				var comparison = TransferStagedReplacement.compareRetainedStage(report.file(), to);
				assertEquals(committed, comparison.tables().getFirst().matchesTarget());
				assertTrue(comparison.tables().getFirst().matchesRecordedStaging());
				Files.writeString(review.prepared().sourceSchemaFile(), "<!-- altered -->", StandardOpenOption.APPEND);
				assertThrows(IllegalArgumentException.class, () -> TransferStagedReplacement.compareRetainedStage(report.file(), to));
			} else if (mode.equals("FAIL_INSERT")) {
				var failure = assertThrows(TransferJobExecutionService.ExecutionException.class, () -> service.execute(review, bindings));
				assertEquals(TransferStagedReplacement.State.ROLLED_BACK, failure.outcome().staging().state());
				try (var rows = b.executeQuery("SELECT ID FROM ITEMS")) { assertTrue(rows.next()); assertEquals(99, rows.getInt(1)); }
			} else {
				var outcome = service.execute(review, bindings);
				assertEquals(TransferStagedReplacement.State.COMMITTED, outcome.staging().state());
				assertTrue(outcome.verificationResult().isMatch());
				if (mode.equals("MULTI")) assertEquals(2, outcome.staging().cleanedStages().size());
				try (var rows = b.executeQuery("SELECT COUNT(*) FROM ITEMS WHERE ID=1")) {
					assertTrue(rows.next()); assertEquals(mode.equals("FULLSCAN") ? 2 : 1, rows.getInt(1));
				}
			}
		}
	}
}
