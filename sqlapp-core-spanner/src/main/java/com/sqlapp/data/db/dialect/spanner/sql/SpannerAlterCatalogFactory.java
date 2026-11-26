/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.SimpleAlterSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.State;

/** Database options and the existing modeled role/schema collections. */
public class SpannerAlterCatalogFactory extends SimpleAlterSqlFactory<Catalog, SpannerSqlBuilder> {
	public static final String DEFAULT_SEQUENCE_KIND = "DEFAULT_SEQUENCE_KIND";
	public static final String VERSION_RETENTION_PERIOD = "VERSION_RETENTION_PERIOD";
	public static final String OPTIMIZER_VERSION = "OPTIMIZER_VERSION";
	public static final String OPTIMIZER_STATISTICS_PACKAGE = "OPTIMIZER_STATISTICS_PACKAGE";
	public static final String DEFAULT_LEADER = "DEFAULT_LEADER";
	private static final List<String> OPTIONS = List.of(DEFAULT_SEQUENCE_KIND, VERSION_RETENTION_PERIOD,
			OPTIMIZER_VERSION, OPTIMIZER_STATISTICS_PACKAGE, DEFAULT_LEADER);
	private static final List<String> COLLECTIONS = List.of(SchemaObjectProperties.ROLES.getLabel(),
			SchemaObjectProperties.ROLE_MEMBERS.getLabel(), SchemaObjectProperties.SCHEMAS.getLabel(),
			SchemaObjectProperties.OBJECT_PRIVILEGES.getLabel(), SchemaObjectProperties.COLUMN_PRIVILEGES.getLabel(),
			SchemaObjectProperties.SCHEMA_PRIVILEGES.getLabel());

	@Override
	public List<SqlOperation> createSql(final Catalog catalog) {
		throw new UnsupportedOperationException("Spanner ALTER DATABASE requires original and target Catalog models");
	}

	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference difference) {
		final var target = difference.getTarget(Catalog.class);
		final var changes = new java.util.TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
		changes.putAll(difference.toDifference().getChangedProperties(getDialect()));
		final boolean streamsChanged = changes.remove(SpannerChangeStreams.KEY) != null;
		for (final var property : changes.keySet())
			if (OPTIONS.stream().noneMatch(property::equalsIgnoreCase)
					&& COLLECTIONS.stream().noneMatch(property::equalsIgnoreCase))
				throw new UnsupportedOperationException("Spanner catalog change is not modeled: " + property);
		final List<SqlOperation> result = new ArrayList<>();
		final var streams = streamsChanged ? planStreams(difference, target, changes.containsKey(COLLECTIONS.get(2)))
				: new StreamPlan(List.of(), List.of());
		final var changedOptions = OPTIONS.stream().filter(changes::containsKey).toList();
		if (!changedOptions.isEmpty()) {
			if (target.getName() == null || !target.getName().matches("[a-z][a-z0-9_-]{0,28}[a-z0-9]"))
				throw new IllegalArgumentException("Spanner ALTER DATABASE requires its short database ID");
			final var builder = createSqlBuilder(getDialect());
			builder.alter().space()._add("DATABASE ").name(target.getName()).space()._add("SET OPTIONS (");
			for (int i = 0; i < changedOptions.size(); i++) {
				final String key = changedOptions.get(i);
				builder._add(i == 0 ? "" : ", ")._add(key.toLowerCase(Locale.ROOT))._add(" = ")
						._add(optionValue(key, target.getSpecifics().get(key)));
			}
			builder._add(")");
			result.add(createOperation(builder.toString(), SqlType.ALTER, difference.getOriginal(), target));
		}
		// Revoke membership before dropping roles; grant only after roles/schemas
		// exist.
		for (final String property : List.of(COLLECTIONS.get(4), COLLECTIONS.get(3), COLLECTIONS.get(5),
				COLLECTIONS.get(1), COLLECTIONS.get(0), COLLECTIONS.get(2))) {
			if (COLLECTIONS.get(2).equals(property))
				result.addAll(streams.beforeSchemas());
			final var collection = (DbObjectDifferenceCollection) changes.get(property);
			if (collection == null)
				continue;
			for (final var item : collection.getList(State.Deleted))
				result.addAll(getSqlFactoryRegistry().createSql(item));
		}
		for (final String property : COLLECTIONS) {
			final var collection = (DbObjectDifferenceCollection) changes.get(property);
			if (collection == null) {
				if (COLLECTIONS.get(2).equals(property))
					result.addAll(streams.afterSchemas());
				continue;
			}
			if (!COLLECTIONS.get(2).equals(property) && !collection.getList(State.Modified).isEmpty())
				throw new UnsupportedOperationException(
						"Spanner role/member changes require explicit removal and addition");
			for (final var item : collection.getList(State.Added))
				if (!COLLECTIONS.get(1).equals(property))
					result.addAll(getSqlFactoryRegistry().createSql(item));
			for (final var item : collection.getList(State.Modified))
				result.addAll(getSqlFactoryRegistry().createSql(item));
			if (COLLECTIONS.get(2).equals(property))
				result.addAll(streams.afterSchemas());
		}
		final var members = (DbObjectDifferenceCollection) changes.get(COLLECTIONS.get(1));
		if (members != null)
			for (final var item : members.getList(State.Added))
				result.addAll(getSqlFactoryRegistry().createSql(item));
		return result;
	}

	private record StreamPlan(List<SqlOperation> beforeSchemas, List<SqlOperation> afterSchemas) {
	}

	private StreamPlan planStreams(final DbObjectDifference difference, final Catalog target,
			final boolean schemaChanges) {
		final var before = new ArrayList<SqlOperation>();
		final var after = new ArrayList<SqlOperation>();
		final var desired = SpannerChangeStreams.get(target);
		if (desired.isEmpty())
			return new StreamPlan(before, after);
		final Catalog original = difference.getOriginal(Catalog.class);
		final var known = SpannerChangeStreams.get(original).orElseThrow(() -> new IllegalArgumentException(
				"Set the original Spanner change stream snapshot explicitly, even when empty"));
		final var old = SpannerChangeStreams.index(known);
		final var next = SpannerChangeStreams.index(desired.get());
		for (final var stream : old.values())
			if (!next.containsKey(stream.name()))
				before.add(
						createOperation(stream.dropSql(getOptions().isDropIfExists()), SqlType.DROP, original, target));
		for (final var stream : next.values()) {
			final var previous = old.get(stream.name());
			if (previous == null) {
				after.add(createOperation(stream.createSql(getOptions().isCreateIfNotExists()), SqlType.CREATE,
						original, target));
				continue;
			}
			if (!previous.name().equals(stream.name()))
				throw new UnsupportedOperationException("Spanner change stream names cannot be changed in place");
			if (previous.scope() != stream.scope() || !previous.targets().equals(stream.targets())) {
				if (schemaChanges)
					throw new UnsupportedOperationException(
							"Apply Spanner change stream scope and schema changes separately");
				after.add(createOperation(stream.setScopeSql(), SqlType.ALTER, original, target));
			}
			final var was = previous.options();
			final var value = stream.options();
			if (!java.util.Objects.equals(was, value)) {
				if (was != null && (value == null || (was.retention() != null && value.retention() == null)
						|| (was.capture() != null && value.capture() == null)
						|| !value.filters().keySet().containsAll(was.filters().keySet())))
					throw new UnsupportedOperationException(
							"Specify replacement values for omitted Spanner change stream options");
				if (value != null
						&& (value.retention() != null || value.capture() != null || !value.filters().isEmpty()))
					after.add(createOperation(stream.setOptionsSql(), SqlType.ALTER, original, target));
			}
		}
		return new StreamPlan(before, after);
	}

	private String optionValue(final String key, final String value) {
		if (value == null && DEFAULT_SEQUENCE_KIND.equals(key))
			throw new UnsupportedOperationException("Spanner default_sequence_kind cannot be unset once configured");
		if (value == null)
			return "NULL";
		if (value.isBlank() || value.chars().anyMatch(Character::isISOControl))
			throw new IllegalArgumentException("Invalid Spanner database option " + key);
		if (DEFAULT_SEQUENCE_KIND.equals(key) && !"bit_reversed_positive".equals(value))
			throw new IllegalArgumentException("Spanner default_sequence_kind requires bit_reversed_positive");
		if (OPTIMIZER_VERSION.equals(key))
			return Long.toString(SpannerCounterRestart.value(value, key));
		if (VERSION_RETENTION_PERIOD.equals(key)) {
			if (!value.matches("[1-9][0-9]*[smhd]"))
				throw new IllegalArgumentException("Invalid Spanner retention duration");
			final long multiplier = switch (value.charAt(value.length() - 1)) {
			case 'm' -> 60;
			case 'h' -> 3600;
			case 'd' -> 86400;
			default -> 1;
			};
			final var seconds = new BigInteger(value.substring(0, value.length() - 1))
					.multiply(BigInteger.valueOf(multiplier));
			if (seconds.compareTo(BigInteger.valueOf(3600)) < 0 || seconds.compareTo(BigInteger.valueOf(604800)) > 0)
				throw new IllegalArgumentException("Spanner retention duration must be between 1h and 7d");
		}
		return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
	}
}
