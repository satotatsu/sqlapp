/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.mdb.rowiterator.MdbRowIteratorHandler;
import com.sqlapp.data.db.dialect.mdb.util.MdbRelationshipSupport;
import com.sqlapp.data.db.dialect.mdb.util.MdbColumnSupport;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;

import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;
import io.github.spannm.jackcess.PropertyMap;
import io.github.spannm.jackcess.query.Query;

/**
 * Loads an Access MDB/ACCDB file without passing identifiers through SQL.
 * <p>
 * Table rows are read lazily. Saved SELECT and UNION queries, including
 * parameters, are represented as views whose definition contains the Access
 * SQL. Action, hidden and parameterized queries are not represented as views.
 * Access complex columns remain unsupported by the row iterator because a
 * single Schema column cannot preserve attachment or multi-value structure.
 * </p>
 */
public final class MdbFileLoader {
	/** Access index flag stored without converting it to a partial index. */
	public static final String INDEX_IGNORE_NULLS = "IGNORE_NULLS";
	/** Native type identity retained by the assessment snapshot. */
	public static final String SOURCE_TYPE = "access.sourceType";
	/** Native Jet/ACE file format retained by the assessment snapshot. */
	public static final String SOURCE_FILE_FORMAT = "access.fileFormat";
	/**
	 * Access text-column empty-string policy retained by the assessment snapshot.
	 */
	public static final String ALLOW_ZERO_LENGTH = "access.allowZeroLength";
	/**
	 * Parameter declarations of an Access saved SELECT/UNION; definition includes
	 * PARAMETERS.
	 */
	public static final String SAVED_QUERY_PARAMETERS = "access.parameters";

	private MdbFileLoader() {
	}

	/** Metadata-only inventory. Linked databases are never opened. */
	public static MdbAssessmentSnapshot loadForAssessment(final Path file) throws IOException {
		final Schema schema = new Schema("").setProductName("Microsoft Access");
		final List<String> links = new ArrayList<>();
		final List<MdbAssessmentSnapshot.SavedQuery> queries = new ArrayList<>();
		try (Database database = new DatabaseBuilder().withPath(file.toAbsolutePath().normalize()).withReadOnly(true)
				.open()) {
			schema.getSpecifics().put(SOURCE_FILE_FORMAT, database.getFileFormat().name());
			database.setLinkResolver((source, name) -> {
				throw new IOException("Linked databases must not be opened during assessment");
			});
			for (final var metadata : database.newTableMetaDataIterable()) {
				if (metadata.isSystem()) {
					continue;
				}
				if (metadata.isLinked()) {
					links.add(metadata.getName());
				} else {
					final var source = metadata.open(database);
					final Table table = toTable(source);
					schema.getTables().add(table);
				}
			}
			// getRelationships() may resolve linked tables. Do not follow those links.
			if (links.isEmpty()) {
				loadRelationships(database, schema);
			}
			queries.addAll(assessmentQueries(database.getQueries()));
		}
		return new MdbAssessmentSnapshot(schema, links, queries, links.isEmpty());
	}

	static List<MdbAssessmentSnapshot.SavedQuery> assessmentQueries(final Iterable<Query> source) {
		final var queries = new ArrayList<MdbAssessmentSnapshot.SavedQuery>();
		for (final Query query : source) {
			queries.add(new MdbAssessmentSnapshot.SavedQuery(query.getName(), query.getType().name(), query.isHidden(),
					!query.getParameters().isEmpty()));
		}
		return List.copyOf(queries);
	}

	public static Schema load(final Path file) throws IOException {
		return loadSchema(file);
	}

	public static Schema loadSchema(final Path file) throws IOException {
		final Path normalized = file.toAbsolutePath().normalize();
		final Schema schema = new Schema("");
		try (Database database = new DatabaseBuilder().withPath(normalized).withReadOnly(true).open()) {
			schema.getSpecifics().put(SOURCE_FILE_FORMAT, database.getFileFormat().name());
			loadTables(database, normalized, schema);
			loadRelationships(database, schema);
			loadQueries(database.getQueries(), schema);
		}
		return schema;
	}

	static void loadQueries(final Iterable<Query> queries, final Schema schema) {
		for (final Query query : queries) {
			if (query.isHidden() || (query.getType() != Query.Type.SELECT && query.getType() != Query.Type.UNION)) {
				continue;
			}
			final View view = new View(query.getName());
			view.setDefinition(query.toSQLString());
			if (!query.getParameters().isEmpty())
				view.getSpecifics().put(SAVED_QUERY_PARAMETERS, String.join("; ", query.getParameters()));
			schema.getViews().add(view);
		}
	}

	public static Table loadTable(final Path file, final String tableName) throws IOException {
		final Path normalized = file.toAbsolutePath().normalize();
		try (Database database = new DatabaseBuilder().withPath(normalized).withReadOnly(true).open()) {
			final Schema schema = new Schema("");
			loadTables(database, normalized, schema);
			loadRelationships(database, schema);
			final Table table = schema.getTables().get(tableName);
			if (table == null) {
				throw new IllegalArgumentException("Access table not found: " + tableName);
			}
			return table;
		}
	}

	private static void loadTables(final Database database, final Path file, final Schema schema) throws IOException {
		for (final String tableName : database.getTableNames()) {
			final io.github.spannm.jackcess.Table source = database.getTable(tableName);
			if (source == null || source.isSystem()) {
				continue;
			}
			final Table table = toTable(source);
			table.setRowIteratorHandler(new MdbRowIteratorHandler(file, tableName));
			schema.getTables().add(table);
		}
	}

	private static Table toTable(final io.github.spannm.jackcess.Table source) throws IOException {
		final Table table = new Table(source.getName());
		if (source.getCreatedDate() != null) {
			table.setCreatedAt(Timestamp.valueOf(source.getCreatedDate()));
		}
		if (source.getUpdatedDate() != null) {
			table.setLastAlteredAt(Timestamp.valueOf(source.getUpdatedDate()));
		}
		final PropertyMap tableProperties = source.getProperties();
		table.setRemarks(toString(tableProperties.getValue(PropertyMap.DESCRIPTION_PROP)));
		final String validationRule = toString(tableProperties.getValue(PropertyMap.VALIDATION_RULE_PROP));
		if (validationRule != null) {
			table.getConstraints().addCheckConstraint(null, validationRule);
		}
		for (final io.github.spannm.jackcess.Column sourceColumn : source.getColumns()) {
			final Column column = MdbColumnSupport.read(sourceColumn);
			table.getColumns().add(column);
		}
		for (final io.github.spannm.jackcess.Index sourceIndex : source.getIndexes()) {
			if (MdbRelationshipSupport.isRelationshipIndex(sourceIndex)) {
				continue;
			}
			final List<Column> keyColumns = new ArrayList<>();
			for (final io.github.spannm.jackcess.Index.Column sourceColumn : sourceIndex.getColumns()) {
				keyColumns.add(table.getColumns().get(sourceColumn.getName()));
			}
			if (sourceIndex.isPrimaryKey()) {
				table.setPrimaryKey(sourceIndex.getName(), keyColumns.toArray(Column[]::new));
				continue;
			}
			final Index index = new Index(sourceIndex.getName());
			index.setUnique(sourceIndex.isUnique());
			if (sourceIndex.shouldIgnoreNulls()) {
				index.getSpecifics().put(INDEX_IGNORE_NULLS, Boolean.TRUE.toString());
			}
			for (int i = 0; i < sourceIndex.getColumns().size(); i++) {
				final io.github.spannm.jackcess.Index.Column sourceColumn = sourceIndex.getColumns().get(i);
				index.getColumns().add(
						new ReferenceColumn(keyColumns.get(i), sourceColumn.isAscending() ? Order.Asc : Order.Desc));
			}
			table.getIndexes().add(index);
		}
		return table;
	}

	private static String toString(final Object value) {
		return value == null ? null : value.toString();
	}

	private static void loadRelationships(final Database database, final Schema schema) throws IOException {
		for (final io.github.spannm.jackcess.Relationship relationship : database.getRelationships()) {
			final Table relatedTable = schema.getTables().get(relationship.getFromTable().getName());
			final Table table = schema.getTables().get(relationship.getToTable().getName());
			if (table == null || relatedTable == null) {
				continue;
			}
			final var key = MdbRelationshipSupport.toForeignKey(relationship);
			MdbRelationshipSupport.bindColumns(key, table, relatedTable);
			table.getConstraints().add(key);
		}
	}

}
