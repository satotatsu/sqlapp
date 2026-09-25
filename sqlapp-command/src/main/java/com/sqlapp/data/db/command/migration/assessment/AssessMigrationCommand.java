/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentProvider;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

import lombok.Getter;
import lombok.Setter;

/** Inspects Schema XML and optionally its source database, then writes a reviewable JSON report. */
@Getter
@Setter
public class AssessMigrationCommand extends AbstractDataSourceCommand {
	private File schemaFile;
	private File outputFile;
	private String targetVersion;
	private String targetCharacterSet;
	private MigrationAssessment.Method migrationMethod;
	private boolean failOnBlockers = true;
	private boolean scanCharacterData;
	@Setter(lombok.AccessLevel.NONE)
	private Report report;

	public record Source(String catalog, String schema, String product, Integer majorVersion, Integer minorVersion,
			Integer revision) { }
	public record Report(int formatVersion, String schemaFingerprint, String targetVersion,
			MigrationAssessment.Method migrationMethod, List<Source> sources, String status,
			MigrationAssessment assessment, String targetCharacterSet) {
		public Report(final int formatVersion, final String schemaFingerprint, final String targetVersion,
				final MigrationAssessment.Method migrationMethod, final List<Source> sources, final String status,
				final MigrationAssessment assessment) {
			this(formatVersion, schemaFingerprint, targetVersion, migrationMethod, sources, status, assessment, null);
		}
	}

	@Override
	protected void doRun() {
		report = null;
		if (schemaFile == null || !schemaFile.isFile()) {
			throw new CommandException("schemaFile must be an existing Schema or Catalog XML file");
		}
		if (outputFile == null || targetVersion == null || targetVersion.isBlank() || migrationMethod == null) {
			throw new CommandException("outputFile, targetVersion and migrationMethod (DIRECT_UPGRADE or LOGICAL_MIGRATION) are required");
		}
		if (scanCharacterData && getDataSource() == null) {
			throw new CommandException("scanCharacterData=true requires a configured dataSource");
		}
		try {
			final var input = schemaFile.toPath().toRealPath();
			final var output = outputFile.toPath().toAbsolutePath().normalize();
			if (input.equals(output) || Files.exists(output) && Files.isSameFile(input, output)) {
				throw new CommandException("outputFile must not overwrite schemaFile");
			}
			final String fingerprint = fingerprint(schemaFile);
			final var root = SchemaUtils.readXml(schemaFile);
			final List<Schema> schemas = new ArrayList<>();
			if (root instanceof Schema schema) {
				schemas.add(schema);
			} else if (root instanceof Catalog catalog) {
				schemas.addAll(catalog.getSchemas());
			} else {
				throw new CommandException("schemaFile root must be Schema or Catalog");
			}
			if (schemas.isEmpty()) {
				throw new CommandException("schemaFile must contain at least one Schema");
			}
			final var provider = MigrationAssessmentProvider.resolve(schemas.getFirst().getProductName(), targetVersion);
			final MigrationAssessment assessment;
			if (getDataSource() == null) {
				assessment = provider.assess(List.copyOf(schemas), targetVersion, migrationMethod, targetCharacterSet);
			} else {
				final MigrationAssessment[] holder = new MigrationAssessment[1];
				executeNoTranAndClose(getDataSource(), connection -> {
					makeReadOnly(connection);
					holder[0] = provider.assess(connection, List.copyOf(schemas), targetVersion, migrationMethod,
							targetCharacterSet, scanCharacterData);
				});
				assessment = holder[0];
				if (assessment == null) {
					throw new CommandException("Online migration assessment did not produce a result");
				}
			}
			if (!fingerprint.equals(fingerprint(schemaFile))) {
				throw new CommandException("schemaFile changed during assessment; retry with a stable snapshot");
			}
			final var sources = schemas.stream().map(schema -> new Source(schema.getCatalogName(), schema.getName(),
					schema.getProductName(), schema.getProductMajorVersion(), schema.getProductMinorVersion(),
					schema.getProductRevision())).toList();
			report = new Report(1, fingerprint, targetVersion, migrationMethod, sources,
					assessment.hasBlockers() ? "BLOCKED" : "REVIEW_REQUIRED", assessment, targetCharacterSet);
			final var converter = new JsonConverter();
			converter.setIndentOutput(true);
			AtomicMigrationFile.write(output, temporary -> converter.writeJsonValue(temporary.toFile(), report));
		} catch (final Exception e) {
			throw e instanceof CommandException commandException ? commandException
					: new CommandException("Migration assessment failed: " + e.getMessage(), e);
		}
		info("Migration assessment: ", report.status());
		if (failOnBlockers && report.assessment().hasBlockers()) {
			throw new CommandException("Migration blockers found; review report: " + outputFile);
		}
	}

	private static void makeReadOnly(final Connection connection) throws Exception {
		if (!connection.isReadOnly()) {
			connection.setReadOnly(true);
		}
	}

	private static String fingerprint(final File file) throws Exception {
		final var digest = MessageDigest.getInstance("SHA-256");
		try (final var stream = Files.newInputStream(file.toPath())) {
			final byte[] buffer = new byte[8192];
			int count;
			while ((count = stream.read(buffer)) != -1) {
				digest.update(buffer, 0, count);
			}
		}
		return "sha256:" + HexFormat.of().formatHex(digest.digest());
	}
}
