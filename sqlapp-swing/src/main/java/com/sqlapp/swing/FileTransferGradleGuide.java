/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.Path;

/**
 * Instructions only; never opens files, connects, or starts a Gradle
 * invocation.
 */
final class FileTransferGradleGuide {
	private FileTransferGradleGuide() {
	}

	static String text(Path exportJob, Path manifest, String fingerprint) {
		return text(exportJob, manifest, fingerprint, null, null);
	}

	/**
	 * @param sourceSettings data source settings file of the source form, or null
	 *                       for environment variables
	 * @param targetSettings the same for the target
	 */
	static String text(Path exportJob, Path manifest, String fingerprint, Path sourceSettings, Path targetSettings) {
		final var text = new StringBuilder("""
				// Apply com.sqlapp.db in your Gradle project and configure JDBC driver
				// and sqlapp dialect runtime dependencies for each required endpoint.
				// GUI-loaded JARs and connection profiles are not inherited by Gradle.
				// Move the entire completed package together; after moving it, update
				// manifestFile below. Do not edit package members or their hashes.

				""");
		if (exportJob != null)
			text.append("""
					// Optional: export a NEW package from the saved job (source only).
					%s
					tasks.named('exportTransferFile') {
					    configurationFile.set(file(%s))
					%s}
					// PowerShell: .\\gradlew.bat exportTransferFile (Unix: ./gradlew)
					// A new export produces a different manifest path/fingerprint.
					// Review that new package and update the import settings before importing it.

					""".formatted(supply("SOURCE", sourceSettings), DirectTransferGradleGuide.pathLiteral(exportJob),
					dataSource("SOURCE", sourceSettings)));
		return text.append("""
				// Import THIS completed package (target only; no source connection).
				%s
				tasks.named('importTransferFile') {
				    manifestFile.set(file(%s))
				    expectedManifestFingerprint.set('%s')
				    allowExecution.set(providers.gradleProperty('allowImport').map { it.toBoolean() }.orElse(false))
				%s}
				// Inspect the package warnings and target before authorizing import.
				// PowerShell: .\\gradlew.bat importTransferFile -PallowImport=true
				// Unix: ./gradlew importTransferFile -PallowImport=true
				// Without allowImport=true the task refuses execution; it is not a review mode.
				// Gradle performs fresh target validation; this fingerprint pins package
				// bytes and does not approve the GUI's reviewed target state.
				// Requested DDL and committed rows may remain after failure. No automatic retry.
				// Results: build/sqlapp-transfer/import (unique child per execution).
				""".formatted(supply("TARGET", targetSettings), DirectTransferGradleGuide.pathLiteral(manifest),
				fingerprint, dataSource("TARGET", targetSettings))).toString();
	}

	private static String supply(String prefix, Path settings) {
		return settings != null
				? "// Connection from the data source settings file used by the GUI (password: file, passwordEnv or a second file)."
				: "// Supply " + prefix + "_JDBC_URL, " + prefix + "_JDBC_USER and " + prefix + "_JDBC_PASSWORD.";
	}

	private static String dataSource(String prefix, Path settings) {
		if (settings != null)
			return "    dataSource.properties(" + DirectTransferGradleGuide.pathLiteral(settings) + ")\n";
		return "    dataSource.jdbcUrl.set(providers.environmentVariable('" + prefix + "_JDBC_URL'))\n"
				+ "    dataSource.username.set(providers.environmentVariable('" + prefix + "_JDBC_USER'))\n"
				+ "    dataSource.password.set(providers.environmentVariable('" + prefix + "_JDBC_PASSWORD'))\n";
	}
}
