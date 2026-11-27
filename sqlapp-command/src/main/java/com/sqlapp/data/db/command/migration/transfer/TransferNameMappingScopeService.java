/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;
import com.sqlapp.util.MessageDigests;

/**
 * Preview removal of excluded mapping groups; never modifies configuration
 * files.
 */
public final class TransferNameMappingScopeService {
	public record Excluded(String sourceKey, String resolvedSource, String targetName, int columnOverrides) {
	}

	public record Review(Path schemaFile, String schemaFingerprint, String mappingFingerprint, List<Excluded> excluded,
			TransferNameMappingIO.Configuration retained) {
		public Review {
			schemaFile = schemaFile.toAbsolutePath().normalize();
			excluded = List.copyOf(excluded);
		}
	}

	public Review review(Path schemaFile, List<String> selected, TransferNameMappingIO.Configuration mapping)
			throws Exception {
		return review(new BulkMigrationJobDraftBuilder().inspect(schemaFile), selected, mapping);
	}

	/** Pin and resolve identities using the same source resolver as execution. */
	public Review review(BulkMigrationJobDraftBuilder.SchemaSnapshot snapshot, List<String> selected,
			TransferNameMappingIO.Configuration mapping) throws Exception {
		if (selected == null || selected.isEmpty()
				|| selected.stream().anyMatch(name -> name == null || name.isBlank()))
			throw new IllegalArgumentException("Choose a nonempty saved table selection.");
		var io = new TransferNameMappingIO();
		String text = io.format(mapping);
		io.validate(text);
		var schema = TransferFileJobDraftBuilder.schema(snapshot);
		var included = TransferSchemaOnlyPreparation.select(schema, selected).stream().map(table -> table.getName())
				.collect(java.util.stream.Collectors.toSet());
		var sources = TransferNameMappingProjection.mappingSources(schema, mapping);
		var retained = new LinkedHashMap<String, TransferNameMappingIO.TableMapping>();
		var excluded = new ArrayList<Excluded>();
		mapping.tables().forEach((key, rule) -> {
			var source = sources.get(key);
			if (included.contains(source.getName()))
				retained.put(key, rule);
			else
				excluded.add(new Excluded(key, TransferSchemaOnlyPreparation.identity(source.getCatalogName(),
						source.getSchemaName(), source.getName()), rule.targetTable(), rule.columns().size()));
		});
		return new Review(snapshot.file(), snapshot.fingerprint(),
				"sha256:" + MessageDigests.SHA256.checksumAsString(text.getBytes(StandardCharsets.UTF_8)), excluded,
				new TransferNameMappingIO.Configuration(1, retained));
	}
}
