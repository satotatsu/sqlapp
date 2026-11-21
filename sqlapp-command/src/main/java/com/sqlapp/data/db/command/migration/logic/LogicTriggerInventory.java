/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.logic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Acquisition;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.ColumnChange;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.ColumnInfo;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.ColumnResolution;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Diagnostic;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Eligibility;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Exclusion;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.ObjectEntry;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.ObjectIdentity;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.TableChange;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.TableIdentity;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.TableResolution;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.exceptions.CommandException;

/**
 * Classifies Oracle table triggers for a conversion request and infers the
 * target tables and columns they refer to.
 * <p>
 * The inventory keeps every trigger of the source model, including those that
 * are not sent to the converter, so that the request shows the whole scope.
 * Target tables and columns are matched by name only. The match is an
 * inference, not a resolved transfer mapping, so it is checked across the whole
 * model: a target table or column chosen by more than one source table or
 * column is left unresolved instead of being asserted.
 */
final class LogicTriggerInventory {
	static final String KIND_TRIGGER = "TRIGGER";
	private static final Set<String> DML_EVENTS = Set.of("INSERT", "UPDATE", "DELETE");
	private static final Pattern EVENT_SEPARATOR = Pattern.compile("\\s+OR\\s+", Pattern.CASE_INSENSITIVE);

	record Inventory(List<ObjectEntry> objects, List<TableChange> tableChanges, List<Diagnostic> diagnostics) {
	}

	private record TableMatch(Table table, TableResolution resolution, String failure) {
	}

	private LogicTriggerInventory() {
	}

	static Inventory build(final List<Schema> sourceSchemas, final List<Schema> targetSchemas,
			final List<Exclusion> exclusions) {
		final var excluded = validateExclusions(exclusions);
		final Map<Table, TableMatch> tableMatches = matchTables(sourceSchemas, targetSchemas);
		final List<Trigger> triggers = new ArrayList<>();
		for (final Schema schema : sourceSchemas) {
			triggers.addAll(schema.getTriggers());
		}
		triggers.sort(Comparator.comparing((Trigger t) -> nz(tableSchema(t))).thenComparing(t -> nz(t.getTableName()))
				.thenComparing(t -> nz(t.getSchemaName())).thenComparing(t -> nz(t.getName())));
		final List<ObjectEntry> objects = new ArrayList<>();
		final Map<Table, TableChange> changes = new LinkedHashMap<>();
		final Set<ObjectIdentity> matchedExclusions = new HashSet<>();
		int sequence = 0;
		for (final Trigger trigger : triggers) {
			final String sourceId = "s" + (++sequence);
			final ObjectIdentity identity = identity(trigger, sourceSchemas);
			final String definition = definition(trigger);
			final boolean available = definition != null && !definition.isBlank();
			final Acquisition acquisition = available ? Acquisition.AVAILABLE : Acquisition.MISSING_DEFINITION;
			final String fingerprint = available ? sha256(definition) : null;
			final List<String> events = events(trigger);
			final Table sourceTable = sourceTable(trigger, sourceSchemas);
			TableMatch match = null;
			Eligibility eligibility;
			String reason;
			final Exclusion exclusion = excluded.get(identity);
			if (exclusion != null) {
				matchedExclusions.add(identity);
				eligibility = Eligibility.EXCLUDED;
				reason = exclusion.reason();
			} else if (!available) {
				eligibility = Eligibility.UNKNOWN_FORM;
				reason = "The trigger definition is not available in the source Schema XML";
			} else {
				final String unsupported = unsupportedForm(trigger, definition, events);
				if (unsupported != null) {
					eligibility = Eligibility.UNSUPPORTED_FORM;
					reason = unsupported;
				} else if (!knownForm(trigger)) {
					eligibility = Eligibility.UNKNOWN_FORM;
					reason = "Unrecognised timing or orientation: timing=" + trigger.getActionTiming()
							+ ", orientation=" + trigger.getActionOrientation();
				} else if (sourceTable == null) {
					eligibility = Eligibility.TARGET_TABLE_UNRESOLVED;
					reason = "The source table is not in the source Schema XML";
				} else {
					match = tableMatches.get(sourceTable);
					if (match.table() == null) {
						eligibility = Eligibility.TARGET_TABLE_UNRESOLVED;
						reason = match.failure();
					} else {
						eligibility = Eligibility.REQUESTED;
						reason = null;
					}
				}
			}
			if (eligibility == Eligibility.REQUESTED) {
				final TableMatch resolved = match;
				changes.computeIfAbsent(sourceTable, table -> tableChange(table, resolved));
			}
			objects.add(new ObjectEntry(sourceId, identity,
					match == null || match.table() == null ? null : tableIdentity(match.table()),
					match == null || match.table() == null ? null : match.resolution(), acquisition, fingerprint,
					eligibility, reason, trigger.getActionTiming(), trigger.getActionOrientation(), events,
					trigger.isEnable()));
		}
		final List<Diagnostic> diagnostics = new ArrayList<>();
		for (final ObjectIdentity identity : excluded.keySet()) {
			if (!matchedExclusions.contains(identity)) {
				diagnostics.add(new Diagnostic("WARN", "EXCLUSION_NOT_FOUND",
						"Excluded trigger was not found in the source Schema XML: " + describe(identity)));
			}
		}
		for (final TableChange change : changes.values()) {
			final List<String> unresolved = change.columns().stream()
					.filter(column -> column.resolution() == ColumnResolution.UNRESOLVED)
					.map(column -> column.source().name()).toList();
			if (!unresolved.isEmpty()) {
				diagnostics.add(new Diagnostic("WARN", "COLUMN_MAPPING_UNRESOLVED",
						"Column correspondences of " + change.sourceTable().schema() + "." + change.sourceTable().name()
								+ " are not resolved by name: " + unresolved));
			}
		}
		return new Inventory(List.copyOf(objects), List.copyOf(changes.values()), List.copyOf(diagnostics));
	}

	private static Map<ObjectIdentity, Exclusion> validateExclusions(final List<Exclusion> exclusions) {
		final Map<ObjectIdentity, Exclusion> result = new LinkedHashMap<>();
		if (exclusions == null) {
			return result;
		}
		for (final Exclusion exclusion : exclusions) {
			if (exclusion == null || exclusion.identity() == null) {
				throw new CommandException("Each exclusion requires an identity");
			}
			final ObjectIdentity identity = exclusion.identity();
			if (!KIND_TRIGGER.equals(identity.kind())) {
				throw new CommandException("Only TRIGGER exclusions are supported: " + describe(identity));
			}
			if (identity.table() == null || identity.schema() == null) {
				throw new CommandException(
						"A trigger exclusion requires schema, name and table: " + describe(identity));
			}
			if (exclusion.reason() == null || exclusion.reason().isBlank()) {
				throw new CommandException("A trigger exclusion requires a non-empty reason: " + describe(identity));
			}
			if (result.putIfAbsent(identity, exclusion) != null) {
				throw new CommandException("Duplicate trigger exclusion: " + describe(identity));
			}
		}
		return result;
	}

	/**
	 * Recognised forms that are not converted. The trigger type recorded by the
	 * metadata reader is authoritative; the definition is inspected only when the
	 * type is missing, and then only its declaration.
	 */
	private static String unsupportedForm(final Trigger trigger, final String definition, final List<String> events) {
		if (trigger.getTableName() == null || trigger.getTableName().isBlank()) {
			return "Not a table trigger (schema or database event triggers are not converted)";
		}
		final String orientation = trigger.getActionOrientation();
		if (orientation == null || orientation.isBlank()) {
			if (OracleTriggerHeader.declaresCompound(definition)) {
				return "Oracle compound triggers are not converted in this version";
			}
		} else if ("COMPOUND".equalsIgnoreCase(orientation)) {
			return "Oracle compound triggers are not converted in this version";
		} else if ("EVENT".equalsIgnoreCase(orientation)) {
			return "Event triggers are not converted";
		}
		if ("INSTEAD OF".equalsIgnoreCase(trigger.getActionTiming())) {
			return "INSTEAD OF triggers are not converted in this version";
		}
		for (final String event : events) {
			if (!DML_EVENTS.contains(event)) {
				return "Only INSERT, UPDATE and DELETE triggers are converted: event=" + event;
			}
		}
		return null;
	}

	private static boolean knownForm(final Trigger trigger) {
		final String timing = trigger.getActionTiming();
		final String orientation = trigger.getActionOrientation();
		return ("BEFORE".equalsIgnoreCase(timing) || "AFTER".equalsIgnoreCase(timing))
				&& ("ROW".equalsIgnoreCase(orientation) || "STATEMENT".equalsIgnoreCase(orientation))
				&& !events(trigger).isEmpty();
	}

	/**
	 * Joins the definition lines the same way Schema XML stores them, so that the
	 * fingerprint matches the text written to source.xml.
	 */
	static String definition(final Trigger trigger) {
		final List<String> lines = trigger.getDefinition();
		if (lines == null || lines.isEmpty()) {
			return null;
		}
		final StringBuilder builder = new StringBuilder();
		for (final String line : lines) {
			builder.append(line).append('\n');
		}
		return builder.toString();
	}

	static List<String> events(final Trigger trigger) {
		final Set<String> result = new TreeSet<>();
		for (final String value : trigger.getEventManipulation()) {
			if (value == null) {
				continue;
			}
			for (final String event : EVENT_SEPARATOR.split(value.trim())) {
				if (!event.isBlank()) {
					result.add(event.trim().toUpperCase(Locale.ROOT));
				}
			}
		}
		return List.copyOf(result);
	}

	private static ObjectIdentity identity(final Trigger trigger, final List<Schema> sourceSchemas) {
		final String catalog = catalogOf(trigger.getSchemaName(), sourceSchemas);
		final TableIdentity table = trigger.getTableName() == null || trigger.getTableName().isBlank() ? null
				: new TableIdentity(catalog, tableSchema(trigger), trigger.getTableName());
		return new ObjectIdentity(KIND_TRIGGER, catalog, trigger.getSchemaName(), trigger.getName(), table);
	}

	private static String catalogOf(final String schemaName, final List<Schema> schemas) {
		for (final Schema schema : schemas) {
			if (Objects.equals(schema.getName(), schemaName)) {
				return blankToNull(schema.getCatalogName());
			}
		}
		return null;
	}

	private static String tableSchema(final Trigger trigger) {
		return trigger.getTableSchemaName() != null ? trigger.getTableSchemaName() : trigger.getSchemaName();
	}

	/**
	 * Finds the source table by exact spelling; the collection lookup may ignore
	 * case.
	 */
	private static Table sourceTable(final Trigger trigger, final List<Schema> sourceSchemas) {
		if (trigger.getTableName() == null) {
			return null;
		}
		final String schemaName = tableSchema(trigger);
		for (final Schema schema : sourceSchemas) {
			if (Objects.equals(schema.getName(), schemaName)) {
				for (final Table table : schema.getTables()) {
					if (table.getName().equals(trigger.getTableName())) {
						return table;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Matches every source table, then rejects target tables chosen by more than
	 * one source table.
	 */
	private static Map<Table, TableMatch> matchTables(final List<Schema> sourceSchemas,
			final List<Schema> targetSchemas) {
		final Map<Table, TableMatch> matches = new IdentityHashMap<>();
		final Map<Table, List<Table>> claims = new IdentityHashMap<>();
		for (final Schema schema : sourceSchemas) {
			for (final Table source : schema.getTables()) {
				final TableMatch match = targetTable(source, targetSchemas);
				matches.put(source, match);
				if (match.table() != null) {
					claims.computeIfAbsent(match.table(), key -> new ArrayList<>()).add(source);
				}
			}
		}
		for (final Map.Entry<Table, List<Table>> claim : claims.entrySet()) {
			if (claim.getValue().size() < 2) {
				continue;
			}
			final List<String> sources = claim.getValue().stream().map(LogicTriggerInventory::qualified).toList();
			for (final Table source : claim.getValue()) {
				matches.put(source, new TableMatch(null, null, "Target table " + qualified(claim.getKey())
						+ " is matched by several source tables: " + sources));
			}
		}
		return matches;
	}

	private static TableMatch targetTable(final Table source, final List<Schema> targetSchemas) {
		final List<Table> exact = new ArrayList<>();
		final List<Table> ignoreCase = new ArrayList<>();
		final List<Table> nameOnly = new ArrayList<>();
		for (final Schema schema : targetSchemas) {
			for (final Table table : schema.getTables()) {
				if (!table.getName().equalsIgnoreCase(source.getName())) {
					continue;
				}
				final boolean sameSchemaIgnoreCase = schema.getName() != null && source.getSchemaName() != null
						&& schema.getName().equalsIgnoreCase(source.getSchemaName());
				if (sameSchemaIgnoreCase && Objects.equals(schema.getName(), source.getSchemaName())
						&& table.getName().equals(source.getName())) {
					exact.add(table);
				} else if (sameSchemaIgnoreCase) {
					ignoreCase.add(table);
				} else {
					nameOnly.add(table);
				}
			}
		}
		if (exact.size() == 1) {
			return new TableMatch(exact.getFirst(), TableResolution.EXACT, null);
		}
		if (exact.isEmpty() && ignoreCase.size() == 1) {
			return new TableMatch(ignoreCase.getFirst(), TableResolution.IGNORE_CASE, null);
		}
		if (exact.isEmpty() && ignoreCase.isEmpty() && nameOnly.size() == 1) {
			return new TableMatch(nameOnly.getFirst(), TableResolution.TABLE_NAME_ONLY, null);
		}
		final List<Table> candidates = new ArrayList<>(exact);
		candidates.addAll(ignoreCase);
		candidates.addAll(nameOnly);
		if (candidates.isEmpty()) {
			return new TableMatch(null, null, "No target table matches " + qualified(source));
		}
		return new TableMatch(null, null, "Ambiguous target tables for " + qualified(source) + ": "
				+ candidates.stream().map(LogicTriggerInventory::qualified).toList());
	}

	/**
	 * Matches each source column by exact name, then by name ignoring case, and
	 * leaves unresolved every source column whose target column is also chosen by
	 * another source column.
	 */
	private static TableChange tableChange(final Table source, final TableMatch match) {
		final Table target = match.table();
		final List<Column> sourceColumns = new ArrayList<>(source.getColumns());
		final List<Column> chosen = new ArrayList<>();
		final List<List<String>> candidates = new ArrayList<>();
		final Map<String, List<String>> claims = new LinkedHashMap<>();
		for (final Column column : sourceColumns) {
			final List<Column> exact = new ArrayList<>();
			final List<Column> ignoreCase = new ArrayList<>();
			for (final Column targetColumn : target.getColumns()) {
				if (targetColumn.getName().equals(column.getName())) {
					exact.add(targetColumn);
				} else if (targetColumn.getName().equalsIgnoreCase(column.getName())) {
					ignoreCase.add(targetColumn);
				}
			}
			final List<Column> all = new ArrayList<>(exact);
			all.addAll(ignoreCase);
			candidates.add(all.stream().map(Column::getName).toList());
			final Column selected = exact.size() == 1 ? exact.getFirst()
					: exact.isEmpty() && ignoreCase.size() == 1 ? ignoreCase.getFirst() : null;
			chosen.add(selected);
			if (selected != null) {
				claims.computeIfAbsent(selected.getName(), key -> new ArrayList<>()).add(column.getName());
			}
		}
		final List<ColumnChange> columns = new ArrayList<>();
		final Set<String> referenced = new HashSet<>();
		for (int i = 0; i < sourceColumns.size(); i++) {
			final Column column = sourceColumns.get(i);
			final Column selected = chosen.get(i);
			final List<String> names = candidates.get(i);
			referenced.addAll(names);
			if (names.isEmpty()) {
				columns.add(new ColumnChange(column(column), null, ColumnResolution.UNMATCHED, List.of(), null));
			} else if (selected == null) {
				columns.add(new ColumnChange(column(column), null, ColumnResolution.UNRESOLVED, names,
						"Several target columns match the name"));
			} else if (claims.get(selected.getName()).size() > 1) {
				columns.add(new ColumnChange(column(column), null, ColumnResolution.UNRESOLVED,
						List.of(selected.getName()), "Target column " + selected.getName()
								+ " is matched by several source columns: " + claims.get(selected.getName())));
			} else {
				final ColumnResolution resolution = selected.getName().equals(column.getName()) ? ColumnResolution.EXACT
						: ColumnResolution.IGNORE_CASE;
				columns.add(new ColumnChange(column(column), column(selected), resolution, List.of(), null));
			}
		}
		final List<String> unmatched = new ArrayList<>();
		for (final Column column : target.getColumns()) {
			if (!referenced.contains(column.getName())) {
				unmatched.add(column.getName());
			}
		}
		return new TableChange(tableIdentity(source), tableIdentity(target), match.resolution(), List.copyOf(columns),
				List.copyOf(unmatched));
	}

	private static ColumnInfo column(final Column column) {
		return new ColumnInfo(column.getName(), column.getDataType() == null ? null : column.getDataType().name(),
				column.getDataTypeName(), column.getLength(), column.getScale(), column.isNotNull());
	}

	private static TableIdentity tableIdentity(final Table table) {
		final Schema schema = table.getSchema();
		return new TableIdentity(schema == null ? null : blankToNull(schema.getCatalogName()), table.getSchemaName(),
				table.getName());
	}

	private static String qualified(final Table table) {
		return table.getSchemaName() + "." + table.getName();
	}

	static String describe(final ObjectIdentity identity) {
		final StringBuilder builder = new StringBuilder();
		builder.append(identity.kind()).append(' ');
		if (identity.catalog() != null) {
			builder.append(identity.catalog()).append('.');
		}
		builder.append(identity.schema()).append('.').append(identity.name());
		if (identity.table() != null) {
			builder.append(" ON ").append(identity.table().schema()).append('.').append(identity.table().name());
		}
		return builder.toString();
	}

	static String sha256(final String value) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return "sha256:" + HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static String blankToNull(final String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String nz(final String value) {
		return value == null ? "" : value;
	}
}
