/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.util.MessageDigests;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingInspectionService.*;

/**
 * Standalone offline HTML evidence from an immutable inspection result; no
 * database access.
 */
public final class TransferMappingPreviewReportIO {
	private static final int MAX_BYTES = 16_777_216;

	public record ColumnRef(String sourceTable, String sourceColumn) {
		public ColumnRef {
			if (sourceTable == null || sourceTable.isBlank() || sourceColumn == null || sourceColumn.isBlank())
				throw new IllegalArgumentException(
						"Report column references require source table and column identities.");
		}
	}

	public record Saved(Path file, String fingerprint, int includedColumns, int totalColumns) {
	}

	private record Row(Table table, Column column) {
	}

	private record Prepared(String html, int included, int total) {
	}

	/** Full captured result in its original table/column order. */
	public String render(Result result) {
		return prepare(result, null).html();
	}

	/**
	 * A non-null selection exports only those exact columns, in selection order.
	 */
	public String render(Result result, List<ColumnRef> selection) {
		return prepare(result, selection).html();
	}

	public Saved writeNew(Path destination, Result result) throws IOException {
		return writeNew(destination, result, null, List.of());
	}

	/**
	 * Recheck recorded bytes before opening; not a file lock or execution approval.
	 */
	public Path verifySaved(Saved saved) throws IOException {
		Objects.requireNonNull(saved, "saved preview report");
		Path file = Objects.requireNonNull(saved.file(), "saved file").toAbsolutePath().normalize();
		String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
		if ((!name.endsWith(".html") && !name.endsWith(".htm")) || saved.fingerprint() == null
				|| !saved.fingerprint().matches("sha256:[0-9a-f]{64}"))
			throw new IllegalArgumentException("A saved HTML preview and its SHA-256 fingerprint are required.");
		if (!Files.isRegularFile(file))
			throw new IOException(
					"Saved preview report was not found. Save a new report or locate the original file: " + file);
		String current = com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile.sha256(file,
				(long) MAX_BYTES, "the 16 MiB preview-report limit", "Saved preview report");
		if (!saved.fingerprint().equals(current))
			throw new IOException(
					"Saved preview report changed after saving. Save a new preview report before opening: " + file);
		return file;
	}

	/**
	 * New report only; selection null means all. Inputs are protected and existing
	 * destinations are rejected with optimistic checks, not a cross-process lock.
	 */
	public Saved writeNew(Path destination, Result result, List<ColumnRef> selection, List<Path> protectedInputs)
			throws IOException {
		Objects.requireNonNull(result, "inspection result");
		Path file = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
		String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (!name.endsWith(".html") && !name.endsWith(".htm"))
			throw new IllegalArgumentException("Choose a .html or .htm preview report file.");
		var inputs = new ArrayList<Path>(Objects.requireNonNull(protectedInputs, "protected inputs"));
		inputs.add(result.schemaFile());
		for (Path input : inputs)
			if (canonical(file).equals(canonical(Objects.requireNonNull(input, "protected input"))))
				throw new IOException(
						"Choose a report destination separate from the job, mapping and captured Schema inputs.");
		var prepared = prepare(result, selection);
		byte[] bytes = encoded(prepared.html());
		checkNew(file);
		AtomicMigrationFile.write(file, temporary -> {
			Files.write(temporary, bytes);
			checkNew(file);
		});
		return new Saved(file, "sha256:" + MessageDigests.SHA256.checksumAsString(bytes), prepared.included(),
				prepared.total());
	}

	private static Path canonical(Path path) throws IOException {
		Path absolute = path.toAbsolutePath().normalize();
		if (Files.exists(absolute))
			return absolute.toRealPath();
		Path parent = absolute.getParent();
		return parent != null && Files.exists(parent) ? parent.toRealPath().resolve(absolute.getFileName()) : absolute;
	}

	private static void checkNew(Path file) throws IOException {
		if (Files.exists(file, LinkOption.NOFOLLOW_LINKS))
			throw new IOException(
					"Report destination already exists. Choose a new filename to retain the earlier evidence.");
	}

	private Prepared prepare(Result result, List<ColumnRef> selection) {
		Objects.requireNonNull(result, "inspection result");
		var all = new LinkedHashMap<ColumnRef, Row>();
		for (var table : result.tables())
			for (var column : table.columns())
				if (all.putIfAbsent(new ColumnRef(table.source(), column.source()), new Row(table, column)) != null)
					throw new IllegalArgumentException("Report source column identity must resolve uniquely.");
		var rows = new ArrayList<Row>();
		if (selection == null)
			rows.addAll(all.values());
		else {
			var seen = new HashSet<ColumnRef>();
			for (var ref : selection) {
				if (!all.containsKey(ref) || !seen.add(ref))
					throw new IllegalArgumentException(
							"Every selected report column must occur once in the captured inspection result.");
				rows.add(all.get(ref));
			}
		}
		var out = new StringBuilder();
		append(out, """
				<!doctype html><html lang="en"><head><meta charset="UTF-8">
				<meta name="viewport" content="width=device-width,initial-scale=1">
				<title>Column mapping preview</title><style>
				body{font:15px system-ui,sans-serif;margin:2rem;color:#222}
				table{border-collapse:collapse;width:100%}
				th,td{border:1px solid #ccc;padding:.5rem;text-align:left;vertical-align:top}
				th{background:#edf1f5}td{white-space:pre-wrap;overflow-wrap:anywhere}
				th:nth-child(-n+5),td:nth-child(-n+5){min-width:6.5rem}
				th:nth-child(6),td:nth-child(6){min-width:5rem}
				th:nth-child(9),td:nth-child(9){min-width:11rem}
				th:last-child,td:last-child{min-width:18rem;max-width:26rem}
				tr.issue{background:#fff3d9}.scroll{overflow:auto}code{overflow-wrap:anywhere}
				dt{font-weight:bold;margin-top:.5rem}dd{margin-left:0}h1{font-size:1.6rem}
				</style></head><body><h1>Column mapping preview</h1><p><strong>""");
		text(out, selection == null ? "All checked columns" : "Captured filtered view");
		append(out, "</strong>: " + rows.size() + " / " + all.size()
				+ " columns included.</p><p>Recorded offline Schema/name-mapping evidence. This is not current target metadata or execution approval. Names may be unsaved editor proposals; saving this report does not save the mapping or job. Column builder previews do not replace full table-factory CREATE/ALTER SQL. Review compatibility warnings and the actual target before execution.</p><details><summary>Captured evidence</summary><dl>");
		evidence(out, "Source Schema file", Objects.toString(result.schemaFile().getFileName(), ""));
		evidence(out, "Source Schema fingerprint", result.schemaFingerprint());
		evidence(out, "Canonical name mapping fingerprint", result.mappingFingerprint());
		evidence(out, "Resolved target builder",
				result.targetDialect() == null ? "Not requested" : result.targetDialect());
		append(out, "</dl></details><h2>Warnings from the complete captured check</h2><ul>");
		if (result.warnings().isEmpty())
			append(out, "<li>No general warnings recorded. Column preview issues are listed separately below.</li>");
		for (String warning : result.warnings()) {
			append(out, "<li>");
			text(out, warning);
			append(out, "</li>");
		}
		append(out, "</ul><h2>Column mappings</h2><div class=\"scroll\"><table><thead><tr>");
		for (String heading : List.of("Source table", "Mapping source key", "Source column", "Target table",
				"Target column", "Source model type", "Source PK", "Source nullable", "Target definition preview",
				"Preview issue")) {
			append(out, "<th>");
			text(out, heading);
			append(out, "</th>");
		}
		append(out, "</tr></thead><tbody>");
		for (var row : rows) {
			var column = row.column();
			boolean issue = column.previewIssue() != null && !column.previewIssue().isBlank();
			append(out, issue ? "<tr class=\"issue\">" : "<tr>");
			for (Object value : Arrays.asList(row.table().source(), row.table().mappingSource(), column.source(),
					row.table().target(), column.target(), column.sourceType(), column.primaryKey() ? "Yes" : "No",
					column.nullable() ? "Yes" : "No",
					column.targetDefinition() == null ? issue ? "Unavailable" : "Not requested"
							: column.targetDefinition(),
					column.previewIssue())) {
				append(out, "<td>");
				text(out, value);
				append(out, "</td>");
			}
			append(out, "</tr>");
		}
		append(out, "</tbody></table></div></body></html>");
		String html = out.toString();
		encoded(html);
		return new Prepared(html, rows.size(), all.size());
	}

	private void evidence(StringBuilder out, String name, Object value) {
		append(out, "<dt>");
		text(out, name);
		append(out, "</dt><dd><code>");
		text(out, value);
		append(out, "</code></dd>");
	}

	private void text(StringBuilder out, Object value) {
		String text = Objects.toString(value, "");
		if (text.length() > MAX_BYTES)
			throw tooLarge();
		append(out, text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
				.replace("'", "&#39;"));
	}

	private void append(StringBuilder out, String text) {
		if ((long) out.length() + text.length() > MAX_BYTES)
			throw tooLarge();
		out.append(text);
	}

	private static byte[] encoded(String text) {
		if (!StandardCharsets.UTF_8.newEncoder().canEncode(text))
			throw new IllegalArgumentException("Preview report text must contain valid UTF-8 characters.");
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		if (bytes.length > MAX_BYTES)
			throw tooLarge();
		return bytes;
	}

	private static IllegalArgumentException tooLarge() {
		return new IllegalArgumentException("Preview report exceeds 16 MiB. Export a smaller filtered view.");
	}
}
