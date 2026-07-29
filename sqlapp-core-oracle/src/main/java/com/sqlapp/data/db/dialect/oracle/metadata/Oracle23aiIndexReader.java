/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.sql.Oracle23aiCreateIndexFactory;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.VectorDistanceType;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.JsonUtils;

public class Oracle23aiIndexReader extends OracleIndexReader {

	public static final String ORGANIZATION = "ORGANIZATION";
	public static final String TARGET_ACCURACY = "TARGET_ACCURACY";
	public static final String NEIGHBORS = "NEIGHBORS";
	public static final String EFCONSTRUCTION = "EFCONSTRUCTION";
	public static final String NEIGHBOR_PARTITIONS = "NEIGHBOR_PARTITIONS";
	public static final String SAMPLES_PER_PARTITION = "SAMPLES_PER_PARTITION";
	public static final String MIN_VECTORS_PER_PARTITION = "MIN_VECTORS_PER_PARTITION";
	public static final String PARALLEL = "PARALLEL";

	private boolean vectorIndexDetailUnavailable;
	private boolean hybridVectorIndexDetailUnavailable;

	public Oracle23aiIndexReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void setIndexType(final ExResultSet rs, final Index index,
			final String productIndexType) throws SQLException {
		if (!"VECTOR".equalsIgnoreCase(productIndexType)) {
			return;
		}
		index.setIndexType(IndexType.Vector);
		final String subtype = getString(rs, "INDEX_SUBTYPE");
		if ("INMEMORY_NEIGHBOR_GRAPH_HNSW".equalsIgnoreCase(subtype)) {
			index.getSpecifics().put(ORGANIZATION, "HNSW");
		} else if ("NEIGHBOR_PARTITIONS_IVF".equalsIgnoreCase(subtype)) {
			index.getSpecifics().put(ORGANIZATION, "IVF");
		}
		if (subtype != null) {
			index.getSpecifics().put("INDEX_SUBTYPE", subtype);
		}
	}

	@Override
	protected void setMetadataDetail(final Connection connection, final Index index)
			throws SQLException {
		super.setMetadataDetail(connection, index);
		if (index.getIndexType() == IndexType.Domain) {
			setHybridVectorIndexDetail(connection, index);
			return;
		}
		if (index.getIndexType() != IndexType.Vector
				|| vectorIndexDetailUnavailable) {
			return;
		}
		final SqlNode node = getSqlNodeCache().getString("vectorIndexDetails.sql");
		final ParametersContext context = newParametersContext(connection, null,
				index.getSchemaName());
		context.put("indexName", nativeCaseString(connection, index.getName()));
		try {
			execute(connection, node, context, new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(final ExResultSet rs) throws SQLException {
					applyVectorIndexParameters(index, getString(rs, "IDX_PARAMS"));
				}
			});
		} catch (final RuntimeException e) {
			if (!isVectorIndexDetailUnavailable(e)) {
				throw e;
			}
			vectorIndexDetailUnavailable = true;
			logger.warn("Oracle VECTOR index detail metadata is unavailable or not permitted; "
					+ "skipping VECSYS.VECTOR$INDEX details. Basic ALL_INDEXES metadata is retained. "
					+ sqlExceptionMessage(e));
		}
	}

	private void setHybridVectorIndexDetail(final Connection connection,
			final Index index) {
		if (hybridVectorIndexDetailUnavailable) {
			return;
		}
		final SqlNode node = getSqlNodeCache().getString(
				"hybridVectorIndexDetails.sql");
		final ParametersContext context = newParametersContext(connection,
				null, index.getSchemaName());
		context.put("indexName",
				nativeCaseString(connection, index.getName()));
		try {
			execute(connection, node, context, new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(final ExResultSet rs)
						throws SQLException {
					index.setIndexType(IndexType.Vector);
					index.getSpecifics().put(
							Oracle23aiCreateIndexFactory.HYBRID, true);
					final String key = getString(rs, "IXO_CLASS");
					final String value = getString(rs, "IXO_OBJECT");
					if (key != null && value != null) {
						index.getSpecifics().put(key, value);
					}
				}
			});
		} catch (final RuntimeException e) {
			if (!isVectorIndexDetailUnavailable(e)) {
				throw e;
			}
			hybridVectorIndexDetailUnavailable = true;
			logger.warn("Oracle hybrid vector index metadata is unavailable "
					+ "or not permitted; basic domain-index metadata is "
					+ "retained. " + sqlExceptionMessage(e));
		}
	}

	@SuppressWarnings("unchecked")
	static void applyVectorIndexParameters(final Index index, final String json) {
		if (json == null || json.isBlank()) {
			return;
		}
		final Map<String, Object> parameters = JsonUtils.fromJsonString(json, Map.class);
		putSpecific(index, parameters, "accuracy", TARGET_ACCURACY);
		putSpecific(index, parameters, "num_neighbors", NEIGHBORS);
		putSpecific(index, parameters, "efConstruction", EFCONSTRUCTION);
		putSpecific(index, parameters, "neighbor_partitions", NEIGHBOR_PARTITIONS);
		putSpecific(index, parameters, "samples_per_partition", SAMPLES_PER_PARTITION);
		putSpecific(index, parameters, "min_vectors_per_partition", MIN_VECTORS_PER_PARTITION);
		putSpecific(index, parameters, "degree_of_parallelism", PARALLEL);
		putSpecific(index, parameters, "vector_type", "VECTOR_TYPE");
		putSpecific(index, parameters, "vector_dimension", "VECTOR_DIMENSION");
		putSpecific(index, parameters, "indexed_col", "INDEXED_COLUMN");
		final String type = stringValue(parameters, "type");
		if (type != null) {
			index.getSpecifics().put(ORGANIZATION, type.toUpperCase(Locale.ROOT));
		}
		final String distance = stringValue(parameters, "distance");
		if (distance != null) {
			index.setVectorDistanceType(toVectorDistanceType(distance));
		}
	}

	private static void putSpecific(final Index index, final Map<String, Object> parameters,
			final String sourceKey, final String targetKey) {
		final Object value = getIgnoreCase(parameters, sourceKey);
		if (value != null) {
			index.getSpecifics().put(targetKey, value);
		}
	}

	private static String stringValue(final Map<String, Object> parameters,
			final String key) {
		final Object value = getIgnoreCase(parameters, key);
		return value == null ? null : value.toString();
	}

	private static Object getIgnoreCase(final Map<String, Object> parameters,
			final String key) {
		for (final Map.Entry<String, Object> entry : parameters.entrySet()) {
			if (key.equalsIgnoreCase(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	private static VectorDistanceType toVectorDistanceType(final String value) {
		final String normalized = value.trim().replace(' ', '_').toUpperCase(Locale.ROOT);
		return switch (normalized) {
		case "COSINE" -> VectorDistanceType.Cosine;
		case "EUCLIDEAN" -> VectorDistanceType.Euclidean;
		case "EUCLIDEAN_SQUARED" -> VectorDistanceType.EuclideanSquared;
		case "INNER_PRODUCT" -> VectorDistanceType.InnerProduct;
		case "DOT", "DOT_PRODUCT" -> VectorDistanceType.DotProduct;
		case "MANHATTAN" -> VectorDistanceType.Manhattan;
		case "HAMMING" -> VectorDistanceType.Hamming;
		case "JACCARD" -> VectorDistanceType.Jaccard;
		default -> null;
		};
	}

	static boolean isVectorIndexDetailUnavailable(final Throwable throwable) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (current instanceof SQLException sqlException) {
				for (SQLException sql = sqlException; sql != null; sql = sql.getNextException()) {
					if (sql.getErrorCode() == 942 || sql.getErrorCode() == 1031) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static String sqlExceptionMessage(final Throwable throwable) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (current instanceof SQLException) {
				return current.getMessage();
			}
		}
		return throwable.getMessage();
	}
}
