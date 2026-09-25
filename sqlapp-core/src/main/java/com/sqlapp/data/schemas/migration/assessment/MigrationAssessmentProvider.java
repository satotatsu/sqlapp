/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration.assessment;

import java.util.List;
import java.util.ServiceLoader;

import com.sqlapp.data.schemas.Schema;

/** Dialect-owned offline assessment; implementations must not mutate the input or connect to a database. */
public interface MigrationAssessmentProvider {
	boolean supports(String product, String targetVersion);

	MigrationAssessment assess(List<Schema> schemas, String targetVersion, MigrationAssessment.Method method);

	/** Optional character-set assessment. Older providers must not silently ignore it. */
	default MigrationAssessment assess(final List<Schema> schemas, final String targetVersion,
			final MigrationAssessment.Method method, final String targetCharacterSet) {
		if (targetCharacterSet != null) {
			throw new IllegalArgumentException("Character-set assessment is not supported by this provider");
		}
		return assess(schemas, targetVersion, method);
	}

	static MigrationAssessmentProvider resolve(final String product, final String targetVersion) {
		final var providers = ServiceLoader.load(MigrationAssessmentProvider.class).stream()
				.map(ServiceLoader.Provider::get).filter(provider -> provider.supports(product, targetVersion)).toList();
		if (providers.size() != 1) {
			throw new IllegalArgumentException("Expected one migration assessment provider for " + product + " -> "
					+ targetVersion + "; found " + providers.size() + ". Add the matching dialect module to the runtime classpath.");
		}
		return providers.get(0);
	}
}
