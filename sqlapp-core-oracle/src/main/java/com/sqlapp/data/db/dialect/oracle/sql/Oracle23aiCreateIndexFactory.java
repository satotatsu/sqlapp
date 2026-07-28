/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.oracle.metadata.Oracle23aiIndexReader;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.VectorDistanceType;
import com.sqlapp.util.CommonUtils;

/**
 * CREATE INDEX factory for Oracle Database 23ai vector indexes.
 */
public class Oracle23aiCreateIndexFactory extends OracleCreateIndexFactory {

	public static final String ORGANIZATION = Oracle23aiIndexReader.ORGANIZATION;
	public static final String TARGET_ACCURACY = Oracle23aiIndexReader.TARGET_ACCURACY;
	public static final String NEIGHBORS = Oracle23aiIndexReader.NEIGHBORS;
	public static final String EFCONSTRUCTION = Oracle23aiIndexReader.EFCONSTRUCTION;
	public static final String NEIGHBOR_PARTITIONS = Oracle23aiIndexReader.NEIGHBOR_PARTITIONS;
	public static final String SAMPLES_PER_PARTITION = Oracle23aiIndexReader.SAMPLES_PER_PARTITION;
	public static final String MIN_VECTORS_PER_PARTITION = Oracle23aiIndexReader.MIN_VECTORS_PER_PARTITION;
	public static final String PARALLEL = Oracle23aiIndexReader.PARALLEL;
	public static final String HYBRID = "HYBRID";
	public static final String DATASTORE = "DATASTORE";
	public static final String FILTER = "FILTER";
	public static final String LEXER = "LEXER";
	public static final String MODEL = "MODEL";
	public static final String VECTORIZER = "VECTORIZER";
	public static final String VECTOR_IDXTYPE = "VECTOR_IDXTYPE";

	@Override
	public void addObjectDetail(final Index index, final Table table,
			final OracleSqlBuilder builder) {
		if (index.getIndexType() != IndexType.Vector) {
			super.addObjectDetail(index, table, builder);
			OracleAnnotationUtils.addAnnotations(builder, index);
			return;
		}
		if (isHybrid(index)) {
			addHybridVectorIndex(index, table, builder);
			return;
		}
		final String organization = validateVectorIndex(index, table);
		builder.space()._add("VECTOR").index().space().name(index, false).on();
		if (index.getSchemaName() != null && table.getSchemaName() != null
				&& !CommonUtils.eq(index.getSchemaName(), table.getSchemaName())) {
			builder.name(table, true);
		} else {
			builder.name(table, false);
		}
		builder.space()._add("(").name(index.getColumns().get(0)).space()._add(")");
		builder.space()._add("ORGANIZATION").space();
		if ("HNSW".equals(organization)) {
			builder._add("INMEMORY NEIGHBOR GRAPH");
		} else {
			builder._add("NEIGHBOR PARTITIONS");
		}
		if (index.getVectorDistanceType() != null) {
			builder.space()._add("DISTANCE").space()
					._add(toOracleDistance(index.getVectorDistanceType()));
		}
		final Integer targetAccuracy = positiveInteger(index, TARGET_ACCURACY);
		if (targetAccuracy != null) {
			if (targetAccuracy > 100) {
				throw new IllegalArgumentException("TARGET_ACCURACY must be between 1 and 100: "
						+ targetAccuracy);
			}
			builder.space()._add("WITH TARGET ACCURACY").space()._add(targetAccuracy);
		}
		addParameters(index, organization, builder);
		final Integer parallel = positiveInteger(index, PARALLEL);
		if (parallel != null) {
			builder.space()._add("PARALLEL").space()._add(parallel);
		}
		OracleAnnotationUtils.addAnnotations(builder, index);
	}

	private boolean isHybrid(final Index index) {
		return Boolean.TRUE.equals(index.getSpecifics().get(HYBRID, Boolean.class));
	}

	private void addHybridVectorIndex(final Index index, final Table table,
			final OracleSqlBuilder builder) {
		validateHybridVectorIndex(index, table);
		builder.space()._add("HYBRID VECTOR").index().space().name(index, false).on();
		if (index.getSchemaName() != null && table.getSchemaName() != null
				&& !CommonUtils.eq(index.getSchemaName(), table.getSchemaName())) {
			builder.name(table, true);
		} else {
			builder.name(table, false);
		}
		builder.space()._add("(").name(index.getColumns().get(0)).space()._add(")");
		builder.space()._add("PARAMETERS").space()._add("('");
		final List<String> parameters = new ArrayList<>();
		addHybridParameter(index, parameters, DATASTORE);
		addHybridParameter(index, parameters, FILTER);
		addHybridParameter(index, parameters, LEXER);
		addHybridParameter(index, parameters, MODEL);
		addHybridParameter(index, parameters, VECTORIZER);
		addHybridParameter(index, parameters, VECTOR_IDXTYPE);
		builder._add(String.join(" ", parameters))._add("')");
		OracleAnnotationUtils.addAnnotations(builder, index);
	}

	private void validateHybridVectorIndex(final Index index, final Table table) {
		if (table == null) {
			throw new IllegalArgumentException("HYBRID VECTOR index requires a parent table: "
					+ index.getName());
		}
		if (index.getColumns().size() != 1) {
			throw new IllegalArgumentException("HYBRID VECTOR index requires exactly one column: "
					+ index.getName());
		}
		final Column column = table.getColumns().get(index.getColumns().get(0).getName());
		if (column == null || !isHybridVectorSourceType(column.getDataType())) {
			throw new IllegalArgumentException(
					"HYBRID VECTOR index column must have VARCHAR, CLOB, or BLOB data type: "
					+ index.getColumns().get(0).getName());
		}
		final String model = hybridParameter(index, MODEL);
		final String vectorizer = hybridParameter(index, VECTORIZER);
		if (model == null && vectorizer == null) {
			throw new IllegalArgumentException(
					"HYBRID VECTOR index requires MODEL or VECTORIZER: "
					+ index.getName());
		}
		if (model != null && vectorizer != null) {
			throw new IllegalArgumentException(
					"HYBRID VECTOR index MODEL and VECTORIZER are alternatives: "
							+ index.getName());
		}
		if (vectorizer != null
				&& hybridParameter(index, VECTOR_IDXTYPE) != null) {
			throw new IllegalArgumentException(
					"HYBRID VECTOR index VECTORIZER and VECTOR_IDXTYPE "
							+ "cannot be specified together: "
							+ index.getName());
		}
		final String vectorIndexType = hybridParameter(index, VECTOR_IDXTYPE);
		if (vectorIndexType != null
				&& !"HNSW".equalsIgnoreCase(vectorIndexType)
				&& !"IVF".equalsIgnoreCase(vectorIndexType)) {
			throw new IllegalArgumentException(
					"HYBRID VECTOR index VECTOR_IDXTYPE must be HNSW or IVF: "
					+ index.getName());
		}
	}

	private boolean isHybridVectorSourceType(final DataType dataType) {
		return dataType == DataType.VARCHAR
				|| dataType == DataType.CLOB
				|| dataType == DataType.BLOB;
	}

	private void addHybridParameter(final Index index, final List<String> parameters,
			final String key) {
		final String value = hybridParameter(index, key);
		if (value != null) {
			parameters.add(key + " " + value);
		}
	}

	private String hybridParameter(final Index index, final String key) {
		final String value = CommonUtils.trim(index.getSpecifics().get(key));
		if (value != null && (value.indexOf('\'') >= 0
				|| value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
			throw new IllegalArgumentException(key
					+ " must not contain quotes or line breaks: " + index.getName());
		}
		return value;
	}

	private String validateVectorIndex(final Index index, final Table table) {
		if (table == null) {
			throw new IllegalArgumentException("VECTOR index requires a parent table: " + index.getName());
		}
		if (index.getColumns().size() != 1) {
			throw new IllegalArgumentException("VECTOR index requires exactly one column: " + index.getName());
		}
		final Column column = table.getColumns().get(index.getColumns().get(0).getName());
		if (column == null || column.getDataType() != DataType.VECTOR) {
			throw new IllegalArgumentException("VECTOR index column must have VECTOR data type: "
					+ index.getColumns().get(0).getName());
		}
		final String organizationValue = CommonUtils.trim(index.getSpecifics().get(ORGANIZATION));
		final String organization = organizationValue == null ? null
				: organizationValue.toUpperCase(Locale.ROOT);
		if (!"HNSW".equals(organization) && !"IVF".equals(organization)) {
			throw new IllegalArgumentException("Oracle VECTOR index ORGANIZATION must be HNSW or IVF: "
					+ index.getName());
		}
		return organization;
	}

	private void addParameters(final Index index, final String organization,
			final OracleSqlBuilder builder) {
		final List<String> parameters = new ArrayList<>();
		parameters.add("type " + organization);
		if ("HNSW".equals(organization)) {
			addPositiveParameter(index, parameters, NEIGHBORS, "neighbors");
			addPositiveParameter(index, parameters, EFCONSTRUCTION, "efconstruction");
			rejectOption(index, NEIGHBOR_PARTITIONS, organization);
			rejectOption(index, SAMPLES_PER_PARTITION, organization);
			rejectOption(index, MIN_VECTORS_PER_PARTITION, organization);
		} else {
			addPositiveParameter(index, parameters, NEIGHBOR_PARTITIONS, "neighbor partitions");
			addPositiveParameter(index, parameters, SAMPLES_PER_PARTITION, "samples_per_partition");
			addNonNegativeParameter(index, parameters, MIN_VECTORS_PER_PARTITION,
					"min_vectors_per_partition");
			rejectOption(index, NEIGHBORS, organization);
			rejectOption(index, EFCONSTRUCTION, organization);
		}
		if (parameters.size() == 1) {
			return;
		}
		builder.space()._add("PARAMETERS").space()._add("(");
		for (int i = 0; i < parameters.size(); i++) {
			builder.comma(i > 0)._add(parameters.get(i));
		}
		builder.space()._add(")");
	}

	private void addPositiveParameter(final Index index, final List<String> parameters,
			final String key, final String sqlName) {
		final Integer value = positiveInteger(index, key);
		if (value != null) {
			parameters.add(sqlName + " " + value);
		}
	}

	private void addNonNegativeParameter(final Index index, final List<String> parameters,
			final String key, final String sqlName) {
		final Integer value = index.getSpecifics().get(key, Integer.class);
		if (value != null) {
			if (value < 0) {
				throw new IllegalArgumentException(key + " must be zero or greater: " + value);
			}
			parameters.add(sqlName + " " + value);
		}
	}

	private Integer positiveInteger(final Index index, final String key) {
		final Integer value = index.getSpecifics().get(key, Integer.class);
		if (value != null && value <= 0) {
			throw new IllegalArgumentException(key + " must be greater than zero: " + value);
		}
		return value;
	}

	private void rejectOption(final Index index, final String key, final String organization) {
		if (index.getSpecifics().get(key) != null) {
			throw new IllegalArgumentException(key + " is not valid for " + organization
					+ " VECTOR indexes");
		}
	}

	private String toOracleDistance(final VectorDistanceType distance) {
		if (distance == VectorDistanceType.EuclideanSquared) {
			return "EUCLIDEAN SQUARED";
		}
		if (distance == VectorDistanceType.DotProduct
				|| distance == VectorDistanceType.InnerProduct) {
			return "DOT";
		}
		return distance.getSqlValue();
	}
}
