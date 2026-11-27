/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransferMappingPathsTest {
	@TempDir
	Path directory;

	@Test
	void mappingsBesideBelowAndOutsideTheJobResolveBackToTheirOriginalFiles() {
		var job = directory.resolve("project/transfer.yaml");
		for (var mapping : java.util.List.of(directory.resolve("project/names.yaml"),
				directory.resolve("project/maps/日本語 名前.yaml"), directory.resolve("shared/names.yaml"))) {
			String relative = TransferMappingPaths.fromJob(job, mapping);
			assertFalse(Path.of(relative).isAbsolute());
			if (java.io.File.separatorChar == '\\')
				assertFalse(relative.contains("\\"));
			assertEquals(mapping.toAbsolutePath().normalize(),
					job.getParent().resolve(relative).toAbsolutePath().normalize());
			assertEquals(mapping.toAbsolutePath().normalize().toString(),
					TransferMappingPaths.browseHint(job, relative));
		}
		assertEquals(job.toString(), TransferMappingPaths.browseHint(job, ""));
		assertEquals("bad\u0000path", TransferMappingPaths.browseHint(job, "bad\u0000path"));
	}

	@Test
	void movingAJobAndItsMappingTogetherPreservesTheRelationshipAndOtherDrivesStayAbsolute() {
		var job = directory.resolve("original/transfer.yaml");
		var mapping = directory.resolve("original/maps/names.yaml");
		String reference = TransferMappingPaths.fromJob(job, mapping);
		assertEquals(directory.resolve("moved/maps/names.yaml"), directory.resolve("moved").resolve(reference));
		if (java.io.File.separatorChar == '\\') {
			assertEquals(Path.of("D:/shared/names.yaml").toString(),
					TransferMappingPaths.fromJob(Path.of("C:/project/job.yaml"), Path.of("D:/shared/names.yaml")));
		}
	}
}
