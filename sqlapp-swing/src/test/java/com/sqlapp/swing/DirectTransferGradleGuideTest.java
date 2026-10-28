/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.*;

class DirectTransferGradleGuideTest {
	@Test
	void handoffPinsReviewedJobAndKeepsRuntimeSecretsOutsideTheSnippet() {
		final var path = Path.of("jobs", "customer's $archive", "transfer.yaml");
		final var hash = "sha256:" + "a".repeat(64);
		final var config = new TransferJobConfiguration("source", "target", List.of("ITEMS"),
				TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING);
		final var text = DirectTransferGradleGuide
				.text(new TransferJobConfigurationIO.Snapshot(path, "not copied into guide", hash, config));
		assertTrue(text.contains(path.toAbsolutePath().normalize().toString().replace('\\', '/').replace("'", "\\'")));
		assertTrue(text.contains("customer\\'s $archive"));
		assertTrue(text.contains("-PconfigurationFingerprint=" + hash));
		assertTrue(text.contains(".orElse(false)"));
		assertTrue(text.contains("providers.environmentVariable('SOURCE_JDBC_PASSWORD')"));
		assertTrue(text.contains("providers.environmentVariable('TARGET_JDBC_URL')"));
		assertTrue(text.contains("fresh live plan"));
		assertTrue(text.contains("GUI-loaded JARs are not inherited"));
		assertFalse(text.contains("not copied into guide"));
		assertFalse(text.contains("jdbc:"));
	}
}
