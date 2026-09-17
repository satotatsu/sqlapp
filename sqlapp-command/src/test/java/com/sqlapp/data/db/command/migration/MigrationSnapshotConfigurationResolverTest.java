/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;

class MigrationSnapshotConfigurationResolverTest {
	@TempDir Path directory;

	@Test
	void resolvesConciseYamlAgainstSchemaModel() throws Exception {
		final FileSet files = files("effectiveAt: 2026-09-16T00:00:00Z\nreportFile: reports/result.json\n"
				+ "failureReportFile: reports/failure.json\n");
		final var resolved = new MigrationSnapshotConfigurationResolver().resolve(files.yaml().toFile());
		assertEquals("CUSTOMER", resolved.sourceTable().getName());
		assertEquals("CUSTOMER_HISTORY", resolved.targetTable().getName());
		assertEquals(java.time.Instant.parse("2026-09-16T00:00:00Z"), resolved.effectiveAt());
		assertEquals(10_000, resolved.batchSize());
		assertEquals(java.util.List.of("ID"), resolved.definition().keyColumns());
		assertEquals(71, resolved.configurationFingerprint().length());
		assertTrue(resolved.configurationFingerprint().startsWith("sha256:"));
		assertEquals(directory.resolve("reports/result.json").toAbsolutePath().normalize(), resolved.reportFile());
		assertEquals(directory.resolve("reports/failure.json").toAbsolutePath().normalize(),
				resolved.failureReportFile());
	}

	@Test
	void fingerprintIsDeterministicAndChangesWithResolvedSchemaShape() throws Exception {
		final FileSet files = files("effectiveAt: 2026-09-16T00:00:00Z\n");
		final var resolver = new MigrationSnapshotConfigurationResolver();
		final String original = resolver.resolve(files.yaml().toFile()).configurationFingerprint();
		assertEquals(original, resolver.resolve(files.yaml().toFile()).configurationFingerprint());

		final Schema changed = (Schema) com.sqlapp.data.schemas.SchemaUtils.readXml(files.xml().toFile());
		changed.getTables().get("CUSTOMER").getColumns().get("NAME").setLength(200L);
		changed.writeXml(files.xml().toFile());
		assertNotEquals(original, resolver.resolve(files.yaml().toFile()).configurationFingerprint());
	}

	@Test
	void validatesApprovalReportBeforeExecution() throws Exception {
		final FileSet files = files("effectiveAt: 2026-09-16T00:00:00Z\n");
		final var resolver = new MigrationSnapshotConfigurationResolver();
		final var initial = resolver.resolve(files.yaml().toFile());
		final Path approval = directory.resolve("approved.json");
		new MigrationSnapshotApprovalReportIO().write(approval, report(initial.configurationFingerprint()));
		Files.writeString(files.yaml(), Files.readString(files.yaml()) + "approvalReportFile: approved.json\n");

		final var approved = resolver.resolve(files.yaml().toFile());
		assertEquals(approval.toAbsolutePath().normalize(), approved.approvalReportFile());

		Files.writeString(files.yaml(), Files.readString(files.yaml()).replace("2026-09-16T00:00:00Z",
				"2026-09-17T00:00:00Z"));
		final var error = assertThrows(CommandException.class, () -> resolver.resolve(files.yaml().toFile()));
		assertEquals("Migration snapshot approval report configuration fingerprint mismatch", error.getMessage());
	}

	@Test
	void rejectsTamperedReviewFieldsEvenWhenFingerprintIsUnchanged() throws Exception {
		final FileSet files = files("effectiveAt: 2026-09-16T00:00:00Z\n");
		final var resolver = new MigrationSnapshotConfigurationResolver();
		final var initial = resolver.resolve(files.yaml().toFile());
		final MigrationSnapshotApprovalReport valid = report(initial.configurationFingerprint());
		final var tampered = new MigrationSnapshotApprovalReport(valid.formatVersion(), valid.generatedAt(),
				valid.configurationFingerprint(), valid.snapshotId(), "PUBLIC.OTHER_SOURCE", valid.targetTable(),
				valid.keyColumns(), valid.trackedColumns(), valid.expireMissingRows(), valid.effectiveAt(),
				valid.fetchSize(), valid.batchSize(), valid.approvalValidFor());
		new MigrationSnapshotApprovalReportIO().write(directory.resolve("approved.json"), tampered);
		Files.writeString(files.yaml(), Files.readString(files.yaml()) + "approvalReportFile: approved.json\n");

		final var error = assertThrows(CommandException.class, () -> resolver.resolve(files.yaml().toFile()));
		assertEquals("Migration snapshot approval report sourceTable mismatch", error.getMessage());
	}

	@Test
	void rejectsExpiredAndFutureDatedApprovals() throws Exception {
		final FileSet files = files("effectiveAt: 2026-09-16T00:00:00Z\napprovalValidFor: PT1H\n");
		final var resolver = new MigrationSnapshotConfigurationResolver(
				Clock.fixed(Instant.parse("2026-09-16T03:00:00Z"), ZoneOffset.UTC));
		final var initial = resolver.resolveForApproval(files.yaml().toFile());
		final Path approval = directory.resolve("approved.json");
		final var io = new MigrationSnapshotApprovalReportIO();
		io.write(approval, report(initial.configurationFingerprint(), Instant.parse("2026-09-16T01:00:00Z"),
				Duration.ofHours(1)));
		Files.writeString(files.yaml(), Files.readString(files.yaml()) + "approvalReportFile: approved.json\n");
		var error = assertThrows(CommandException.class, () -> resolver.resolve(files.yaml().toFile()));
		assertEquals("Migration snapshot approval report has expired", error.getMessage());

		io.write(approval, report(initial.configurationFingerprint(), Instant.parse("2026-09-16T02:00:00Z"),
				Duration.ofHours(1)));
		error = assertThrows(CommandException.class, () -> resolver.resolve(files.yaml().toFile()));
		assertEquals("Migration snapshot approval report has expired", error.getMessage());

		io.write(approval, report(initial.configurationFingerprint(), Instant.parse("2026-09-16T04:00:00Z"),
				Duration.ofHours(1)));
		error = assertThrows(CommandException.class, () -> resolver.resolve(files.yaml().toFile()));
		assertEquals("Migration snapshot approval report generatedAt is in the future", error.getMessage());

		io.write(approval, report(initial.configurationFingerprint(), Instant.parse("2026-09-16T02:30:00Z"),
				Duration.ofHours(1)));
		assertEquals(Duration.ofHours(1), resolver.resolve(files.yaml().toFile()).approvalValidFor());
	}

	@Test
	void requiresStableEffectiveTimestamp() throws Exception {
		final FileSet files = files("");
		assertThrows(CommandException.class,
				() -> new MigrationSnapshotConfigurationResolver().resolve(files.yaml().toFile()));
	}

	@Test
	void rejectsUnknownColumnsBeforeOpeningDatabaseConnections() throws Exception {
		final FileSet files = files("trackedColumns: [UNKNOWN]\neffectiveAt: 2026-09-16T00:00:00Z\n", false);
		final var error = assertThrows(CommandException.class,
				() -> new MigrationSnapshotConfigurationResolver().resolve(files.yaml().toFile()));
		assertEquals("Unknown column 'UNKNOWN' in sourceTable: CUSTOMER", error.getMessage());
	}

	@Test
	void rejectsInvalidSnapshotSystemColumnTypesBeforeOpeningDatabaseConnections() throws Exception {
		final FileSet files = files("effectiveAt: 2026-09-16T00:00:00Z\n");
		Schema schema = (Schema) com.sqlapp.data.schemas.SchemaUtils.readXml(files.xml().toFile());
		schema.getTables().get("CUSTOMER_HISTORY").getColumns().get("VALID_FROM").setDataType(DataType.VARCHAR);
		schema.writeXml(files.xml().toFile());
		var error = assertThrows(CommandException.class,
				() -> new MigrationSnapshotConfigurationResolver().resolve(files.yaml().toFile()));
		assertEquals("Snapshot validity column must be a date/time type: VALID_FROM", error.getMessage());

		schema = (Schema) com.sqlapp.data.schemas.SchemaUtils.readXml(files.xml().toFile());
		schema.getTables().get("CUSTOMER_HISTORY").getColumns().get("VALID_FROM").setDataType(DataType.TIMESTAMP);
		schema.getTables().get("CUSTOMER_HISTORY").getColumns().get("IS_CURRENT").setDataType(DataType.VARCHAR);
		schema.writeXml(files.xml().toFile());
		error = assertThrows(CommandException.class,
				() -> new MigrationSnapshotConfigurationResolver().resolve(files.yaml().toFile()));
		assertEquals("Snapshot currentColumn must be boolean or numeric: IS_CURRENT", error.getMessage());
	}

	private FileSet files(final String tail) throws Exception {
		return files(tail, true);
	}

	private FileSet files(final String tail, final boolean includeTrackedColumns) throws Exception {
		final Schema schema = new Schema("PUBLIC");
		final Table source = new Table("CUSTOMER");
		source.getColumns().add(new Column("ID").setDataType(DataType.INT));
		source.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
		final Table target = new Table("CUSTOMER_HISTORY");
		target.getColumns().add(new Column("ID").setDataType(DataType.INT));
		target.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
		target.getColumns().add(new Column("VALID_FROM").setDataType(DataType.TIMESTAMP));
		target.getColumns().add(new Column("VALID_TO").setDataType(DataType.TIMESTAMP));
		target.getColumns().add(new Column("IS_CURRENT").setDataType(DataType.BOOLEAN));
		schema.getTables().add(source);
		schema.getTables().add(target);
		final Path xml = directory.resolve("schema.xml");
		schema.writeXml(xml.toFile());
		final Path yaml = directory.resolve("snapshot.yaml");
		Files.writeString(yaml, "schemaFile: schema.xml\nsourceTable: CUSTOMER\ntargetTable: CUSTOMER_HISTORY\n"
				+ "keyColumns: [ID]\n" + (includeTrackedColumns ? "trackedColumns: [NAME]\n" : "")
				+ "validFromColumn: VALID_FROM\n"
				+ "validToColumn: VALID_TO\ncurrentColumn: IS_CURRENT\n" + tail);
		return new FileSet(xml, yaml);
	}

	private record FileSet(Path xml, Path yaml) { }

	private static MigrationSnapshotApprovalReport report(final String fingerprint) {
		return report(fingerprint, Instant.parse("2026-09-16T01:00:00Z"), null);
	}

	private static MigrationSnapshotApprovalReport report(final String fingerprint, final Instant generatedAt,
			final Duration approvalValidFor) {
		return new MigrationSnapshotApprovalReport(MigrationSnapshotApprovalReport.CURRENT_FORMAT_VERSION,
				generatedAt, fingerprint, "CUSTOMER", "PUBLIC.CUSTOMER",
				"PUBLIC.CUSTOMER_HISTORY", java.util.List.of("ID"), java.util.List.of("NAME"), true,
				Instant.parse("2026-09-16T00:00:00Z"), 10_000, 10_000, approvalValidFor);
	}
}
