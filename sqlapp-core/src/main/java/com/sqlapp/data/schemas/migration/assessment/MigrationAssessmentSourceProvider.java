/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration.assessment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/** Read-only file inventory for offline migration diagnosis.
 * Implementations must not connect to databases or follow external links.
 * Uncollected assets and data must be reported explicitly as coverage findings. */
public interface MigrationAssessmentSourceProvider {
	boolean supports(Path file);
	MigrationAssessmentSource load(Path file) throws IOException;

	static MigrationAssessmentSourceProvider resolve(final Path file) {
		return resolve(file, ServiceLoader.load(MigrationAssessmentSourceProvider.class).stream()
				.map(ServiceLoader.Provider::get).toList());
	}

	static MigrationAssessmentSourceProvider resolve(final Path file,
			final List<MigrationAssessmentSourceProvider> providers) {
		final Path normalized = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		final var matches = providers.stream().filter(provider -> provider.supports(normalized)).toList();
		if (matches.size() != 1) {
			throw new IllegalArgumentException("Expected one migration assessment source provider for " + normalized
					+ "; found " + matches.size() + ". Add the matching source module or remove conflicting providers.");
		}
		return matches.getFirst();
	}
}
