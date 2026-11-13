/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import com.sqlapp.data.db.command.migration.transfer.TransferJobConfigurationIO;

/**
 * Credential-free handoff to the existing Gradle task, without changing the
 * saved job.
 */
final class DirectTransferGradleGuide {
	private DirectTransferGradleGuide() {
	}

	static String text(TransferJobConfigurationIO.Snapshot job) {
		return text(job, null, null);
	}

	/**
	 * @param sourceSettings data source settings file of the source form, or null
	 *                       to read the connection from environment variables
	 * @param targetSettings the same for the target
	 */
	static String text(TransferJobConfigurationIO.Snapshot job, java.nio.file.Path sourceSettings,
			java.nio.file.Path targetSettings) {
		return "// Saved transfer mode: " + job.configuration().dataMode() + "\n" + (job.configuration()
				.dataMode() == com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.DataMode.SCHEMA_ONLY
						? "// SCHEMA_ONLY prepares reviewed table definitions; source rows are not transferred or verified.\n// Schema SQL may change target data when explicitly allowed.\n// Schema mode: "
								+ job.configuration().schemaMode() + "\n"
						: "")
				+ """
						// Use this saved YAML unchanged in your Gradle project.
						// Apply com.sqlapp.db and add both JDBC drivers and sqlapp dialects
						// to the project's runtime dependencies. GUI-loaded JARs are not inherited.
						%s
						tasks.named('transferDatabase') {
						    configurationFile.set(file(%s))
						    allowExecution.set(providers.gradleProperty('execute').map { it.toBoolean() }.orElse(false))
						    expectedConfigurationFingerprint.set(providers.gradleProperty('configurationFingerprint'))
						%s%s}

						// Run from that Gradle project (PowerShell; use ./gradlew on Unix).
						// Review only: reads source/target metadata, creates no tables or rows.
						// .\\gradlew.bat transferDatabase
						// Inspect the new Gradle review, then explicitly execute:
						// .\\gradlew.bat transferDatabase -Pexecute=true -PconfigurationFingerprint=%s

						// Execution prepares a fresh live plan; the fingerprint pins YAML bytes,
						// not this GUI's reviewed database state. Check endpoint environment values.
						// Requested DDL and committed data may remain after failure.
						// After editing YAML, review again to obtain its new fingerprint.
						// Gradle reports: build/sqlapp-transfer/direct (unique child per run).
						""".formatted(connectionNote(sourceSettings, targetSettings), pathLiteral(job.file()),
						connection("sourceDataSource", "SOURCE", sourceSettings),
						connection("dataSource", "TARGET", targetSettings), job.fingerprint());
	}

	private static String connectionNote(java.nio.file.Path source, java.nio.file.Path target) {
		final var text = new StringBuilder();
		if (source != null || target != null)
			text.append("// Connections from data source settings files (the same files as the GUI).\n")
					.append("// Their password comes from the file, its passwordEnv variable, or a second file.\n");
		if (source == null || target == null)
			text.append("// Supply ").append(source == null ? "SOURCE_JDBC_URL/USER/PASSWORD" : "")
					.append(source == null && target == null ? " and " : "")
					.append(target == null ? "TARGET_JDBC_URL/USER/PASSWORD" : "")
					.append("\n// through the environment of the Gradle process.\n");
		return text.toString().stripTrailing();
	}

	/**
	 * One connection block, from a settings file when known, otherwise from
	 * environment variables.
	 */
	private static String connection(String block, String prefix, java.nio.file.Path settings) {
		if (settings != null)
			return "    " + block + " { properties " + pathLiteral(settings) + " }\n";
		return "    " + block + " {\n" //
				+ "        jdbcUrl.set(providers.environmentVariable('" + prefix + "_JDBC_URL'))\n"
				+ "        username.set(providers.environmentVariable('" + prefix + "_JDBC_USER'))\n"
				+ "        password.set(providers.environmentVariable('" + prefix + "_JDBC_PASSWORD'))\n" + "    }\n";
	}

	static String pathLiteral(java.nio.file.Path path) {
		return quote(path.toAbsolutePath().normalize().toString().replace('\\', '/'));
	}

	private static String quote(String value) {
		final var result = new StringBuilder("'");
		for (int index = 0; index < value.length(); index++) {
			final char character = value.charAt(index);
			if (character == '\\' || character == '\'')
				result.append('\\');
			if (Character.isISOControl(character))
				result.append(String.format("\\u%04x", (int) character));
			else
				result.append(character);
		}
		return result.append('\'').toString();
	}
}
