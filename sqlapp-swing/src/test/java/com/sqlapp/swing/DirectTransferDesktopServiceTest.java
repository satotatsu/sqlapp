/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.transfer.*;

class DirectTransferDesktopServiceTest {
	@TempDir
	Path directory;

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void guiAuthoredJobReviewsExecutesAndRunsThroughTheHeadlessCommandUnchanged(boolean createMissing)
			throws Exception {
		final var source = spec();
		final var target = spec();
		final var another = spec();
		for (var spec : List.of(source, target, another))
			try (var c = DriverManager.getConnection(spec.jdbcUrl(), "SA", ""); var s = c.createStatement()) {
				if (spec == source || !createMissing)
					s.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40))");
				if (spec == source)
					s.execute("INSERT INTO ITEMS VALUES(1,'one'),(2,NULL)");
			}
		final var service = new DirectTransferDesktopService();
		final var discovered = (DataMigrationDraftService.Prepared) service.perform(DirectTransferPanel.Action.DISCOVER,
				new DirectTransferPanel.Request(null, directory, source, null, "", null), new char[0], new char[0]);
		final var saved = new TransferJobDraftBuilder().saveDirect(discovered.schema(),
				List.of(discovered.schema().tables().getFirst().table()), 1, true,
				TransferJobConfiguration.CompatibilityPolicy.WARN, null,
				createMissing ? TransferJobConfiguration.SchemaMode.CREATE_MISSING
						: TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING,
				discovered.directory().resolve("transfer.yaml"));
		final byte[] bytes = Files.readAllBytes(saved.file());
		final var review = (DirectTransferDesktopService.Reviewed) service.perform(DirectTransferPanel.Action.REVIEW,
				new DirectTransferPanel.Request(saved.file(), directory, source, target, "", null), new char[0],
				new char[0]);
		assertTrue(Files.isRegularFile(review.report()));
		assertEquals(createMissing, review.schemaSqlDraft() != null);
		assertFalse(Files.readString(review.report()).contains("schemaSqlDraft"));
		if (!createMissing)
			assertEquals(0, count(target));
		else
			assertFalse(review.review().prepared().schemaPreparation().statements().isEmpty());
		final var result = (DirectTransferDesktopService.Executed) service.perform(DirectTransferPanel.Action.EXECUTE,
				new DirectTransferPanel.Request(saved.file(), directory, source, target, "", review), new char[0],
				new char[0]);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.outcome().phase());
		assertTrue(result.outcome().verificationResult().isMatch());
		assertEquals(2, count(target));
		assertTrue(Files.isRegularFile(result.report()));
		assertEquals(createMissing, result.outcome().schemaPreparation().completedStatements() > 0);
		assertEquals(review.schemaSqlDraft(), result.schemaSqlDraft());
		if (createMissing) {
			var original = review.review().prepared();
			var changed = new TransferJobPreparationService.Prepared(original.directory(), original.job(),
					original.sourceSchemaFile(), original.dataJob(), original.planFingerprint(), original.taskIds(),
					original.warnings(), new TransferSchemaPreparation.Plan("changed-plan",
							original.schemaPreparation().statements(), original.warnings()));
			var changedOutcome = new TransferJobExecutionService.Outcome(changed,
					TransferJobExecutionService.Phase.REVALIDATION, null, null, List.of(), Map.of(), null);
			assertNull(DirectTransferDesktopService.draftFor(changedOutcome, review));
		}
		try (var from = PlanningJdbc.open(source, new char[0]);
				var to = PlanningJdbc.openForDataExecution(another, new char[0])) {
			final var command = new TransferDatabaseCommand();
			command.setSourceDataSource(from);
			command.setDataSource(to);
			command.setCloseDataSource(false);
			command.setConfigurationFile(saved.file().toFile());
			command.setWorkspaceDirectory(directory.toFile());
			command.setExpectedConfigurationFingerprint(saved.fingerprint());
			command.setAllowExecution(true);
			command.run();
			assertEquals(TransferJobExecutionService.Phase.COMPLETE, command.getResult().phase());
			assertEquals(2, count(another));
		}
		assertArrayEquals(bytes, Files.readAllBytes(saved.file()));
	}

	@Test
	void invalidInputClearsBothPasswordBuffersWithoutExposingConnectionSecrets() {
		final char[] from = "secret-source".toCharArray(), to = "secret-target".toCharArray();
		final var failure = assertThrows(IllegalArgumentException.class,
				() -> new DirectTransferDesktopService().perform(DirectTransferPanel.Action.REVIEW,
						new DirectTransferPanel.Request(directory.resolve("missing.yaml"), directory,
								new PlanningJdbc.Spec("jdbc:private:secret-source", "u", ""), spec(), "", null),
						from, to));
		assertArrayEquals(new char[from.length], from);
		assertArrayEquals(new char[to.length], to);
		assertNull(failure.getCause());
		assertFalse(failure.getMessage().contains("secret-source"));
		assertFalse(failure.getMessage().contains("secret-target"));
	}

	@Test
	void missingTargetTableKeepsItsActualValidationReasonForTheGui() throws Exception {
		final var source = spec();
		final var target = spec();
		try (var connection = DriverManager.getConnection(source.jdbcUrl(), "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		}
		final var file = Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: source\ntarget: target\ntables: [ITEMS]\nschemaMode: REQUIRE_EXISTING\n");
		final var failure = assertThrows(TransferActionFailure.class,
				() -> new DirectTransferDesktopService().perform(DirectTransferPanel.Action.REVIEW,
						new DirectTransferPanel.Request(file, directory, source, target, "", null), new char[0],
						new char[0]));
		assertTrue(failure.reason.contains("target table was not found"), failure.summary());
		assertTrue(failure.reason.contains("ITEMS"));
		assertTrue(failure.phase.contains("validating target"));
		assertFalse(failure.summary().contains("jdbc:"));
		assertTrue(failure.nextStep.contains("Create missing target tables"));
		assertNull(failure.getCause());
		assertEquals(0, failure.getSuppressed().length);
	}

	@Test
	void copyFailureRetainsCommittedChunksAndWritesTheSharedFailureAudit() throws Exception {
		final var source = spec();
		final var target = spec();
		try (var c = DriverManager.getConnection(source.jdbcUrl(), "SA", ""); var s = c.createStatement()) {
			s.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
			s.execute("INSERT INTO ITEMS VALUES(1),(2)");
		}
		try (var c = DriverManager.getConnection(target.jdbcUrl(), "SA", ""); var s = c.createStatement()) {
			s.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY CHECK(ID<2))");
		}
		final var service = new DirectTransferDesktopService();
		final var prepared = (DataMigrationDraftService.Prepared) service.perform(DirectTransferPanel.Action.DISCOVER,
				new DirectTransferPanel.Request(null, directory, source, null, "", null), new char[0], new char[0]);
		final var job = new TransferJobDraftBuilder().saveDirect(prepared.schema(),
				List.of(prepared.schema().tables().getFirst().table()), 1, true, null, null,
				prepared.directory().resolve("transfer.yaml"));
		final var review = (DirectTransferDesktopService.Reviewed) service.perform(DirectTransferPanel.Action.REVIEW,
				new DirectTransferPanel.Request(job.file(), directory, source, target, "", null), new char[0],
				new char[0]);
		final var failure = assertThrows(DirectTransferDesktopService.ExecutionFailure.class,
				() -> service.perform(DirectTransferPanel.Action.EXECUTE,
						new DirectTransferPanel.Request(job.file(), directory, source, target, "", review), new char[0],
						new char[0]));
		assertEquals(TransferJobExecutionService.Phase.COPY, failure.result.outcome().phase());
		assertEquals(1, count(target));
		assertFalse(failure.result.outcome().committedProgress().isEmpty());
		assertTrue(Files.readString(failure.result.report()).contains("COPY"));
		assertNull(failure.getCause());
		assertEquals(0, failure.getSuppressed().length);
	}

	private static PlanningJdbc.Spec spec() {
		return new PlanningJdbc.Spec("jdbc:hsqldb:mem:direct_gui_" + UUID.randomUUID(), "SA", "");
	}

	private static int count(PlanningJdbc.Spec spec) throws Exception {
		try (var c = DriverManager.getConnection(spec.jdbcUrl(), "SA", "");
				var s = c.createStatement();
				var r = s.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
			r.next();
			return r.getInt(1);
		}
	}
}
