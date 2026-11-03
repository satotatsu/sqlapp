/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.dialect.phoenix.sql.PhoenixDdlOptions;
import com.sqlapp.data.db.dialect.phoenix.sql.PhoenixCreateTableFactory;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** Reads native index headers and columns in at most two set-based queries. */
public class PhoenixIndexReader extends IndexReader {
	public PhoenixIndexReader(Dialect dialect) {
		super(dialect);
	}

	private record Identity(String tenant, String schema, String name) {
	}

	private record Source(Index index, SortedMap<Integer, ReferenceColumn> keys,
			SortedMap<Integer, ReferenceColumn> includes) {
	}

	static String headerResource(ProductVersionInfo version) {
		return PhoenixSequenceReader.sqlResource(version).equals("sequences31.sql") ? "indexes31.sql" : "indexes.sql";
	}

	@Override
	protected List<Index> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		final Map<Identity, Source> sources = new LinkedHashMap<>();
		execute(connection, getSqlNodeCache().getString(headerResource(version)), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				final var index = new Index(rs.getString("index_name"));
				index.setCatalogName(rs.getString("catalog_name"));
				index.setSchemaName(rs.getString("schema_name"));
				index.setTableName(rs.getString("table_name"));
				index.setUnique(false);
				final Long salt = rs.getLongValue("salt_buckets");
				final Integer type = rs.getInteger("index_type");
				if (type != null && type != 1 && type != 2)
					throw new UnsupportedOperationException("Unsupported Phoenix index type: " + type);
				final boolean unknown = type == null && (version == null || version.getMajorVersion() == null
						|| ((version.getMajorVersion() == 3 || version.getMajorVersion() == 4)
								&& version.getMinorVersion() == null));
				final String indexType = unknown ? "UNKNOWN" : type != null && type == 2 ? "LOCAL" : "GLOBAL";
				index.getSpecifics().put("phoenix.indexType", indexType);
				if (salt != null)
					index.getSpecifics().put(PhoenixDdlOptions.SALT_BUCKETS, salt);
				else if (indexType.equals("GLOBAL"))
					index.getSpecifics().put(PhoenixDdlOptions.SALT_BUCKETS, 0);
				final String state = rs.getString("index_state");
				if (state != null)
					index.getSpecifics().put("phoenix.indexState", state);
				final var id = new Identity(index.getCatalogName(), index.getSchemaName(), index.getName());
				if (sources.putIfAbsent(id, new Source(index, new TreeMap<>(), new TreeMap<>())) != null)
					throw new IllegalStateException("Duplicate Phoenix index header: " + id);
			}
		});
		if (sources.isEmpty())
			return List.of();
		final var parameters = context.clone();
		parameters.put("indexNames", sources.keySet().stream().map(Identity::name).distinct().toList());
		execute(connection, getSqlNodeCache().getString("indexColumns.sql"), parameters, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				final var source = sources.get(new Identity(rs.getString("catalog_name"), rs.getString("schema_name"),
						rs.getString("index_name")));
				if (source == null)
					return; // The SQL name set can contain indexes from multiple schemas.
				final String nativeName = rs.getString("column_name");
				if (nativeName == null)
					throw new IllegalStateException("Missing Phoenix index column");
				final int separator = nativeName.indexOf(':');
				final String family = separator > 0 ? nativeName.substring(0, separator) : null;
				final String name = separator >= 0 ? nativeName.substring(separator + 1) : nativeName;
				if (name.isBlank() || name.contains("(") || name.contains(")"))
					throw new UnsupportedOperationException(
							"Phoenix functional/internal index keys require native DDL: " + nativeName);
				final var column = new ReferenceColumn(name);
				if (family != null)
					column.getSpecifics().put(PhoenixCreateTableFactory.COLUMN_FAMILY, family);
				final boolean included = rs.getString("column_family") != null;
				if (!included) {
					final Integer sortOrder = rs.getInteger("sort_order");
					// Phoenix SortOrder serialization IDs, not comparison signs.
					if (sortOrder == null || sortOrder == 2)
						column.setOrder(Order.Asc);
					else if (sortOrder == 1)
						column.setOrder(Order.Desc);
					else
						throw new IllegalStateException("Unknown Phoenix index sort order for "
								+ source.index().getName() + "." + nativeName + ": " + sortOrder);
				}
				final int ordinal = rs.getInt("ordinal_position");
				if (ordinal <= 0 || (included ? source.includes() : source.keys()).putIfAbsent(ordinal, column) != null)
					throw new IllegalStateException("Invalid Phoenix index column ordinal: " + ordinal);
			}
		});
		final List<Index> result = new ArrayList<>();
		for (var source : sources.values()) {
			if (source.keys().isEmpty())
				throw new IllegalStateException("Missing Phoenix index keys: " + source.index().getName());
			final Set<String> names = new HashSet<>();
			for (var column : java.util.stream.Stream
					.concat(source.keys().values().stream(), source.includes().values().stream()).toList())
				if (!names.add(column.getName()))
					throw new UnsupportedOperationException(
							"Repeated Phoenix index source names require a richer column identity: "
									+ column.getName());
			source.keys().values().forEach(c -> source.index().getColumns().add(c));
			source.includes().values().forEach(c -> source.index().getIncludes().add(c));
			result.add(source.index());
		}
		return result;
	}
}
