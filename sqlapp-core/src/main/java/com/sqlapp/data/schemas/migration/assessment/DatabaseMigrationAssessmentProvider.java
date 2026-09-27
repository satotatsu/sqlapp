/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration.assessment;

import java.util.List;
import java.util.ServiceLoader;
import com.sqlapp.data.schemas.Column;

/** Target-owned rules for an explicitly supported source/target/version combination.
 * A SQL dialect alone does not imply assessment support. Implementations must not
 * mutate source metadata, execute SQL or connect to external resources. */
public interface DatabaseMigrationAssessmentProvider {
	boolean supports(String sourceProduct, String targetDatabase, String targetVersion);
	String targetProduct();
	default String normalizeTargetVersion(final String version) { return version; }
	MigrationAssessment assess(MigrationAssessmentSource source, String targetVersion);
	/** Optional validation of an already resolved user mapping. */
	default MigrationAssessment assessMapping(final MigrationAssessmentSource source, final String targetVersion,
			final ResolvedMigrationTargetMapping mapping) {
		return new MigrationAssessment(List.of(), List.of());
	}
	/** Suggested editable target type for a mapping template, or null when an
	 * explicit extraction/conversion decision is required. */
	default String suggestTargetType(final Column sourceColumn, final String targetVersion) { return null; }

	/** Generates review-only target DDL after the mapping has been validated. */
	default String generateTargetDdl(final ResolvedMigrationTargetMapping mapping, final String targetVersion) {
		throw new UnsupportedOperationException("Target DDL preview is not supported by this provider");
	}

	static DatabaseMigrationAssessmentProvider resolve(final String sourceProduct, final String targetDatabase,
			final String targetVersion) {
		return resolve(sourceProduct, targetDatabase, targetVersion,
				ServiceLoader.load(DatabaseMigrationAssessmentProvider.class).stream().map(ServiceLoader.Provider::get).toList());
	}

	static DatabaseMigrationAssessmentProvider resolve(final String sourceProduct, final String targetDatabase,
			final String targetVersion, final List<DatabaseMigrationAssessmentProvider> providers) {
		if (sourceProduct == null || sourceProduct.isBlank() || targetDatabase == null || targetDatabase.isBlank()
				|| targetVersion == null || targetVersion.isBlank()) {
			throw new IllegalArgumentException("sourceProduct, targetDatabase and targetVersion are required");
		}
		final var matches = providers.stream()
				.filter(provider -> provider.supports(sourceProduct, targetDatabase, targetVersion)).toList();
		if (matches.size() != 1) {
			throw new IllegalArgumentException("Expected one database migration assessment provider for " + sourceProduct
					+ " -> " + targetDatabase + " " + targetVersion + "; found " + matches.size()
					+ ". Add a provider supporting this exact combination or remove conflicting providers; a SQL dialect alone is insufficient.");
		}
		return matches.getFirst();
	}
}
