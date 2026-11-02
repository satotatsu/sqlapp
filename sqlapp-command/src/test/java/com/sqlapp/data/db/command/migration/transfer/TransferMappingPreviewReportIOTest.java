/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingInspectionService.*;
import com.sqlapp.data.db.command.migration.transfer.TransferMappingPreviewReportIO.ColumnRef;

class TransferMappingPreviewReportIOTest {
	@Test
	void verifiesSavedBytesAndExplainsChangedMissingAndInvalidSavedReports() throws Exception {
		var saved = io.writeNew(directory.resolve("saved.html"), result());
		assertEquals(saved.file(), io.verifySaved(saved));
		Files.writeString(saved.file(), "changed externally");
		assertTrue(assertThrows(java.io.IOException.class, () -> io.verifySaved(saved)).getMessage()
				.contains("changed after saving"));
		Files.delete(saved.file());
		assertTrue(assertThrows(java.io.IOException.class, () -> io.verifySaved(saved)).getMessage()
				.contains("not found"));
		assertThrows(IllegalArgumentException.class, () -> io.verifySaved(
				new TransferMappingPreviewReportIO.Saved(directory.resolve("program.exe"), saved.fingerprint(), 1, 1)));
		assertThrows(IllegalArgumentException.class, () -> io.verifySaved(
				new TransferMappingPreviewReportIO.Saved(directory.resolve("saved.html"), "untracked", 1, 1)));
	}

	@TempDir
	Path directory;
	private final TransferMappingPreviewReportIO io = new TransferMappingPreviewReportIO();

	private Result result() {
		return new Result(directory.resolve("source.xml"), "sha256:source", "sha256:mapping",
				List.of(new Table("PUBLIC.顧客", "PEOPLE",
						List.of(new Column("FIRST", "OWNER_ID", "INT", true, false, "INT NOT NULL", null),
								new Column("SECOND", "価格<&\"'", "OTHER", false, true, null,
										"Unsupported <script>alert('x')</script>")),
						"顧客")),
				List.of("Inspect <img src=x onerror='x'> & expressions"), "TargetBuilder");
	}

	@Test
	void rendersEscapedPortableFullEvidenceAndRetainsProvenanceWithoutAbsoluteSourcePath() {
		String html = io.render(result());
		assertTrue(html.startsWith("<!doctype html>"));
		assertTrue(html.contains("All checked columns"));
		assertTrue(html.contains("2 / 2 columns included"));
		assertTrue(html.contains("sha256:source"));
		assertTrue(html.contains("sha256:mapping"));
		assertTrue(html.contains("TargetBuilder"));
		assertTrue(html.contains("価格&lt;&amp;&quot;&#39;"));
		assertTrue(html.contains("&lt;script&gt;"));
		assertFalse(html.contains("<script>"));
		assertFalse(html.contains("<img"));
		assertTrue(html.contains("PUBLIC.顧客"));
		assertTrue(html.contains("Mapping source key"));
		assertFalse(html.contains(directory.toString()));
		assertTrue(html.contains("source.xml"));
		assertTrue(html.contains("not current target metadata or execution approval"));
		assertEquals(html, io.render(result()));
	}

	@Test
	void filteredSelectionUsesExactIdentitiesAndRequestedOrderIncludingEmptyViews() {
		String html = io.render(result(),
				List.of(new ColumnRef("PUBLIC.顧客", "SECOND"), new ColumnRef("PUBLIC.顧客", "FIRST")));
		assertTrue(html.contains("Captured filtered view"));
		assertTrue(html.indexOf("SECOND") < html.indexOf("FIRST"));
		String one = io.render(result(), List.of(new ColumnRef("PUBLIC.顧客", "SECOND")));
		assertTrue(one.contains("1 / 2 columns included"));
		assertFalse(one.contains("OWNER_ID"));
		assertTrue(one.contains("Warnings from the complete captured check"));
		assertTrue(io.render(result(), List.of()).contains("0 / 2 columns included"));
	}

	@Test
	void writesUtf8FingerprintAndRefusesToReplaceExistingEvidence() throws Exception {
		Files.writeString(result().schemaFile(), "original source");
		var saved = io.writeNew(directory.resolve("preview.html"), result());
		assertEquals(2, saved.includedColumns());
		assertEquals(2, saved.totalColumns());
		assertEquals(io.render(result()), Files.readString(saved.file(), StandardCharsets.UTF_8));
		assertEquals(
				"sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(Files.readAllBytes(saved.file())),
				saved.fingerprint());
		byte[] before = Files.readAllBytes(saved.file());
		assertThrows(java.io.IOException.class, () -> io.writeNew(saved.file(), result()));
		assertArrayEquals(before, Files.readAllBytes(saved.file()));
		assertEquals("original source", Files.readString(result().schemaFile()));
	}

	@Test
	void rejectsUnknownDuplicateAndInvalidUnicodeRowsBeforeCreatingFiles() throws Exception {
		var destination = directory.resolve("invalid.html");
		assertThrows(IllegalArgumentException.class,
				() -> io.writeNew(destination, result(), List.of(new ColumnRef("Unknown", "FIRST")), List.of()));
		var ref = new ColumnRef("PUBLIC.顧客", "FIRST");
		assertThrows(IllegalArgumentException.class, () -> io.render(result(), List.of(ref, ref)));
		var duplicate = new Result(result().schemaFile(), "source", "mapping",
				List.of(result().tables().getFirst(), result().tables().getFirst()), List.of());
		assertThrows(IllegalArgumentException.class, () -> io.render(duplicate));
		var invalid = new Result(result().schemaFile(), "source", "mapping",
				List.of(new Table("A", "A", List.of(new Column("ID", "\uD800", "INT", false, true)))), List.of());
		assertThrows(IllegalArgumentException.class, () -> io.writeNew(destination, invalid));
		assertFalse(Files.exists(destination));
	}

	@Test
	void protectsInputsAndRejectsOversizedReportsWithoutPartialOutput() throws Exception {
		Path protectedFile = directory.resolve("job.html");
		Files.writeString(protectedFile, "job bytes");
		assertThrows(java.io.IOException.class,
				() -> io.writeNew(protectedFile, result(), null, List.of(protectedFile)));
		assertEquals("job bytes", Files.readString(protectedFile));
		Files.delete(protectedFile);
		assertThrows(java.io.IOException.class,
				() -> io.writeNew(protectedFile, result(), null, List.of(protectedFile)));
		assertFalse(Files.exists(protectedFile));
		var large = new Result(result().schemaFile(), "source", "mapping",
				List.of(new Table("A", "A",
						List.of(new Column("ID", "ID", "INT", false, true, "x".repeat(16_777_217), null)))),
				List.of(), "Builder");
		Path output = directory.resolve("large.html");
		assertThrows(IllegalArgumentException.class, () -> io.writeNew(output, large));
		assertFalse(Files.exists(output));
		assertThrows(IllegalArgumentException.class, () -> io.writeNew(directory.resolve("wrong.yaml"), result()));
	}
}
