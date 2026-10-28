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
		return "// Saved transfer mode: " + job.configuration().dataMode() + "\n" + (job.configuration()
				.dataMode() == com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.DataMode.SCHEMA_ONLY
						? "// SCHEMA_ONLY creates missing selected tables; no rows are copied or verified.\n"
						: "")
				+ """
						// Use this saved YAML unchanged in your Gradle project.
						// Apply com.sqlapp.db and add both JDBC drivers and sqlapp dialects
						// to the project's runtime dependencies. GUI-loaded JARs are not inherited.
						// Supply SOURCE_JDBC_URL/USER/PASSWORD and TARGET_JDBC_URL/USER/PASSWORD
						// through the environment of the Gradle process.
						tasks.named('transferDatabase') {
						    configurationFile.set(file(%s))
						    allowExecution.set(providers.gradleProperty('execute').map { it.toBoolean() }.orElse(false))
						    expectedConfigurationFingerprint.set(providers.gradleProperty('configurationFingerprint'))
						    sourceDataSource {
						        jdbcUrl.set(providers.environmentVariable('SOURCE_JDBC_URL'))
						        username.set(providers.environmentVariable('SOURCE_JDBC_USER'))
						        password.set(providers.environmentVariable('SOURCE_JDBC_PASSWORD'))
						    }
						    dataSource {
						        jdbcUrl.set(providers.environmentVariable('TARGET_JDBC_URL'))
						        username.set(providers.environmentVariable('TARGET_JDBC_USER'))
						        password.set(providers.environmentVariable('TARGET_JDBC_PASSWORD'))
						    }
						}

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
						""".formatted(pathLiteral(job.file()), job.fingerprint());
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
