/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FileTransferGradleGuideTest {
	private static final String HASH = "sha256:" + "b".repeat(64);

	@Test
	void exportGuideSeparatesNewExportsFromThePinnedCompletedPackage() {
		final var text = FileTransferGradleGuide.text(Path.of("jobs", "customer's $job.yaml"),
				Path.of("packages", "manifest.json"), HASH);
		assertTrue(text.contains("customer\\'s $job.yaml"));
		assertTrue(text.contains("tasks.named('exportTransferFile')"));
		assertTrue(text.contains("SOURCE_JDBC_PASSWORD"));
		assertTrue(text.contains("expectedManifestFingerprint.set('" + HASH + "')"));
		assertTrue(text.contains("different manifest path/fingerprint"));
		assertTrue(text.contains(".orElse(false)"));
		assertTrue(text.contains("it is not a review mode"));
		assertFalse(text.contains("jdbc:"));
	}

	@Test
	void importGuideNeedsOnlyTargetAndUsesAbsoluteQuotedManifestPath() {
		final var manifest = Path.of("moved package", "owner's $manifest.json");
		final var text = FileTransferGradleGuide.text(null, manifest, HASH);
		assertTrue(text.contains(DirectTransferGradleGuide.pathLiteral(manifest)));
		assertTrue(text.contains("TARGET_JDBC_PASSWORD"));
		assertTrue(text.contains("-PallowImport=true"));
		assertTrue(text.contains("fresh target validation"));
		assertFalse(text.contains("SOURCE_JDBC"));
		assertFalse(text.contains("tasks.named('exportTransferFile')"));
	}
}
