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
					// Supply SOURCE_JDBC_URL, SOURCE_JDBC_USER and SOURCE_JDBC_PASSWORD.
					tasks.named('exportTransferFile') {
					    configurationFile.set(file(%s))
					    dataSource.jdbcUrl.set(providers.environmentVariable('SOURCE_JDBC_URL'))
					    dataSource.username.set(providers.environmentVariable('SOURCE_JDBC_USER'))
					    dataSource.password.set(providers.environmentVariable('SOURCE_JDBC_PASSWORD'))
					}
					// PowerShell: .\\gradlew.bat exportTransferFile (Unix: ./gradlew)
					// A new export produces a different manifest path/fingerprint.
					// Review that new package and update the import settings before importing it.

					""".formatted(DirectTransferGradleGuide.pathLiteral(exportJob)));
		return text.append("""
				// Import THIS completed package (target only; no source connection).
				// Supply TARGET_JDBC_URL, TARGET_JDBC_USER and TARGET_JDBC_PASSWORD.
				tasks.named('importTransferFile') {
				    manifestFile.set(file(%s))
				    expectedManifestFingerprint.set('%s')
				    allowExecution.set(providers.gradleProperty('allowImport').map { it.toBoolean() }.orElse(false))
				    dataSource.jdbcUrl.set(providers.environmentVariable('TARGET_JDBC_URL'))
				    dataSource.username.set(providers.environmentVariable('TARGET_JDBC_USER'))
				    dataSource.password.set(providers.environmentVariable('TARGET_JDBC_PASSWORD'))
				}
				// Inspect the package warnings and target before authorizing import.
				// PowerShell: .\\gradlew.bat importTransferFile -PallowImport=true
				// Unix: ./gradlew importTransferFile -PallowImport=true
				// Without allowImport=true the task refuses execution; it is not a review mode.
				// Gradle performs fresh target validation; this fingerprint pins package
				// bytes and does not approve the GUI's reviewed target state.
				// Requested DDL and committed rows may remain after failure. No automatic retry.
				// Results: build/sqlapp-transfer/import (unique child per execution).
				""".formatted(DirectTransferGradleGuide.pathLiteral(manifest), fingerprint)).toString();
	}
}
