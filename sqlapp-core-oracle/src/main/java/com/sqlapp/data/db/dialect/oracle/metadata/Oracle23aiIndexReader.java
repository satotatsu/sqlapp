/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
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
import com.sqlapp.util.DoubleKeyMap;

public class Oracle23aiIndexReader extends Oracle11gIndexReader {

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
	protected void setIndexType(final ExResultSet rs, final Index index, final String productIndexType)
			throws SQLException {
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
	protected void setMetadataDetail(final Connection connection, final ParametersContext originalContext,
			final List<Index> indexes) throws SQLException {
		readPartitionDetails(connection, originalContext, indexes);
		readVectorDetails(connection, indexes, false);
		readVectorDetails(connection, indexes, true);
		readIndexDefinitions(connection, indexes);
	}

	@Override
	protected void setMetadataDetail(final Connection connection, final Index index) throws SQLException {
		// Detail metadata is populated once by the collection hook.
	}

	private void readVectorDetails(Connection connection, List<Index> indexes, boolean hybrid) {
		if (hybrid ? hybridVectorIndexDetailUnavailable : vectorIndexDetailUnavailable)
			return;
		DoubleKeyMap<String, String, Index> selected = new DoubleKeyMap<>();
		for (Index index : indexes) {
			if (index.getIndexType() == (hybrid ? IndexType.Domain : IndexType.Vector)) {
				selected.put(index.getSchemaName(), index.getName(), index);
			}
		}
		if (selected.toList().isEmpty())
			return;
		ParametersContext context = new ParametersContext();
		context.put("schemaName", selected.keySet());
		context.put("indexName", selected.secondKeySet());
		SqlNode node = getSqlNodeCache().getString(hybrid ? "hybridVectorIndexDetails.sql" : "vectorIndexDetails.sql");
		try {
			execute(connection, node, context, new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(ExResultSet rs) throws SQLException {
					Index index = selected.get(rs.getString("OWNER"), rs.getString("INDEX_NAME"));
					if (index == null)
						return;
					if (!hybrid) {
						applyVectorIndexParameters(index, rs.getString("IDX_PARAMS"));
						return;
					}
					index.setIndexType(IndexType.Vector);
					index.getSpecifics().put(Oracle23aiCreateIndexFactory.HYBRID, true);
					String key = rs.getString("IXO_CLASS");
					String value = rs.getString("IXO_OBJECT");
					if (key != null && value != null)
						index.getSpecifics().put(key, value);
				}
			});
		} catch (RuntimeException e) {
			if (!isVectorIndexDetailUnavailable(e))
				throw e;
			if (hybrid)
				hybridVectorIndexDetailUnavailable = true;
			else
				vectorIndexDetailUnavailable = true;
			logger.warn("Oracle vector detail catalog is unavailable or not permitted; basic index metadata retained. "
					+ sqlExceptionMessage(e));
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

	private static void putSpecific(final Index index, final Map<String, Object> parameters, final String sourceKey,
			final String targetKey) {
		final Object value = getIgnoreCase(parameters, sourceKey);
		if (value != null) {
			index.getSpecifics().put(targetKey, value);
		}
	}

	private static String stringValue(final Map<String, Object> parameters, final String key) {
		final Object value = getIgnoreCase(parameters, key);
		return value == null ? null : value.toString();
	}

	private static Object getIgnoreCase(final Map<String, Object> parameters, final String key) {
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
