/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.MessageDigests;

/** Schema-only orchestration over the common target-dialect DDL planner. */
final class TransferSchemaOnlyPreparation {
	private TransferSchemaOnlyPreparation() {
	}

	static List<Table> select(Schema schema, List<String> names) {
		final var selected = new ArrayList<Table>();
		for (var name : names) {
			var matches = schema.getTables().stream()
					.filter(table -> name.equals(table.getName())
							|| name.equals(identity(null, schema.getName(), table.getName()))
							|| name.equals(identity(schema.getCatalogName(), schema.getName(), table.getName())))
					.toList();
			if (matches.size() != 1)
				throw new IllegalArgumentException("Source table must resolve uniquely: " + name);
			if (selected.contains(matches.getFirst()))
				throw new IllegalArgumentException("Select each resolved source table only once.");
			selected.add(matches.getFirst());
		}
		return SchemaUtils.getNewSortedTableList(selected, Table.TableOrder.CREATE);
	}

	static TransferJobPreparationService.Prepared prepare(Connection target, Schema schema,
			TransferJobConfigurationIO.Snapshot job, Path directory, Path xml, String catalog, String targetSchema)
			throws Exception {
		return prepare(target, schema, job, directory, xml, catalog, targetSchema, null);
	}

	static TransferJobPreparationService.Prepared prepare(Connection target, Schema schema,
			TransferJobConfigurationIO.Snapshot job, Path directory, Path xml, String catalog, String targetSchema,
			TransferNameMappingIO.Snapshot mapping) throws Exception {
		final var selected = select(schema, job.configuration().tables());
		final var projection = mapping == null ? null
				: TransferNameMappingProjection.project(schema, selected, mapping.configuration());
		final var warnings = new ArrayList<String>();
		if (projection != null && projection.renamedIdentifiers()) {
			String warning = TransferSchemaChangeChecks.MAPPING_EXPRESSIONS
					+ " Table/column names are mapped; SQL expressions, checks, defaults and predicates remain verbatim. Review Schema SQL and adjust source expressions where necessary.";
			TransferSchemaPreparation.compatibility(job.configuration().compatibilityPolicy(), warnings, warning);
		}
		final var tasks = new ArrayList<TransferSchemaPreparation.Selection>();
		for (int index = 0; index < selected.size(); index++) {
			final var source = projection == null ? selected.get(index)
					: projection.bySourceName().get(selected.get(index).getName());
			tasks.add(new TransferSchemaPreparation.Selection("schema-table-" + (index + 1), source,
					source.clone().setCatalogName(catalog).setSchemaName(targetSchema)));
		}
		final var base = TransferSchemaPreparation.schemaOnly(target, projection == null ? schema : projection.schema(),
				tasks, job.fingerprint(), job.configuration().compatibilityPolicy(), job.configuration().schemaMode());
		warnings.addAll(base.warnings());
		final var plan = projection == null ? base
				: TransferSchemaPreparation.result(base.fingerprint(), base.statements(), warnings,
						job.configuration().schemaMode());
		final var io = new TransferJobConfigurationIO();
		if (!io.read(job.file()).fingerprint().equals(job.fingerprint()))
			throw new IllegalArgumentException("Transfer definition changed during schema-only preparation.");
		TransferNameMappingIO.capture(mapping, directory);
		final var saved = io.write(directory.resolve("transfer.yaml"), job.text(), null);
		return new TransferJobPreparationService.Prepared(directory, saved, xml, null,
				fingerprint(xml, saved.fingerprint(), plan.fingerprint(),
						mapping == null ? null : mapping.fingerprint()),
				tasks.stream().map(TransferSchemaPreparation.Selection::getTaskId).toList(), plan.warnings(), plan);
	}

	static String fingerprint(Path xml, String job, String plan) throws java.io.IOException {
		return fingerprint(xml, job, plan, null);
	}

	static String fingerprint(Path xml, String job, String plan, String mapping) throws java.io.IOException {
		final String schema = BoundedMigrationFile.sha256(xml, 16_777_216L, "maxSchemaBytes", "Schema");
		return "sha256:" + MessageDigests.SHA256
				.checksumAsString((schema + "\n" + job + "\n" + plan + (mapping == null ? "" : "\n" + mapping))
						.getBytes(StandardCharsets.UTF_8));
	}

	static String identity(String catalog, String schema, String table) {
		var parts = new ArrayList<String>();
		for (var part : new String[] { catalog, schema, table }) {
			if (part == null || part.isBlank())
				continue;
			if (part.contains("."))
				throw new IllegalArgumentException("Identifiers containing dots require explicit mapping support.");
			parts.add(part);
		}
		return String.join(".", parts);
	}
}
