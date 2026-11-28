/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.IntervalArrays;
import com.sqlapp.data.schemas.Column;

/**
 * Batched native interval collection metadata. Types must exist in the
 * destination.
 */
public final class OracleIntervalCollections {
	public static final String OWNER = "oracle.interval.collection.owner";
	public static final String NAME = "oracle.interval.collection.name";
	public static final String KIND = "oracle.interval.collection.kind";
	private static final Map<Connection, List<Description>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

	private OracleIntervalCollections() {
	}

	public record Description(String owner, String name, String kind, Long bound, DataType element, int leading,
			int fraction) {
		public String qualifiedName() {
			return quote(owner) + "." + quote(name);
		}

		public Column apply(Column column) {
			column.setDataType(element).setDataTypeName(qualifiedName()).setArrayDimension(1).setLength(leading)
					.setScale(element == DataType.INTERVAL_DAY_TO_SECOND ? fraction : null);
			column.getSpecifics().put(OWNER, owner);
			column.getSpecifics().put(NAME, name);
			column.getSpecifics().put(KIND, kind);
			if (bound == null)
				column.getSpecifics().remove(IntervalArrays.DECLARED_BOUNDS);
			else
				column.getSpecifics().put(IntervalArrays.DECLARED_BOUNDS, bound.toString());
			return column;
		}
	}

	private static String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	public static String qualifiedName(Column column) {
		String owner = column.getSpecifics().get(OWNER), name = column.getSpecifics().get(NAME);
		if (owner == null || name == null || owner.isEmpty() || name.isEmpty())
			throw new IllegalArgumentException("Resolve the named Oracle interval collection column before use");
		return quote(owner) + "." + quote(name);
	}

	/**
	 * Refresh after collection DDL; one catalog query fetches all visible interval
	 * collections.
	 */
	public static List<Description> refresh(Connection connection) throws SQLException {
		var result = new ArrayList<Description>();
		try (var statement = connection
				.prepareStatement("SELECT owner,type_name,coll_type,upper_bound,elem_type_name,precision,scale "
						+ "FROM all_coll_types WHERE elem_type_name IN ('INTERVAL YEAR TO MONTH','INTERVAL DAY TO SECOND')");
				var rows = statement.executeQuery()) {
			while (rows.next()) {
				long bound = rows.getLong("UPPER_BOUND");
				Long maximum = rows.wasNull() ? null : bound;
				int leading = rows.getInt("PRECISION");
				if (rows.wasNull())
					leading = 2;
				int fraction = rows.getInt("SCALE");
				if (rows.wasNull())
					fraction = 6;
				result.add(new Description(rows.getString("OWNER"), rows.getString("TYPE_NAME"),
						rows.getString("COLL_TYPE"), maximum,
						rows.getString("ELEM_TYPE_NAME").contains("YEAR") ? DataType.INTERVAL_YEAR_TO_MONTH
								: DataType.INTERVAL_DAY_TO_SECOND,
						leading, fraction));
			}
		}
		var resolved = List.copyOf(result);
		CACHE.put(connection, resolved);
		return resolved;
	}

	private static List<Description> descriptions(Connection connection) throws SQLException {
		var cached = CACHE.get(connection);
		return cached == null ? refresh(connection) : cached;
	}

	/** High-level entry point for manually constructed JDBC/Schema columns. */
	public static Column column(Connection connection, String typeName) throws SQLException {
		var matches = refresh(connection).stream()
				.filter(type -> type.qualifiedName().equals(typeName)
						|| (type.owner() + "." + type.name()).equals(typeName) || type.name().equals(typeName))
				.toList();
		if (matches.size() != 1)
			throw new IllegalArgumentException(
					"Choose one existing, unambiguous Oracle interval collection type: " + typeName);
		return matches.getFirst().apply(new Column());
	}

	public static Description require(Connection connection, Column column) throws SQLException {
		String name = qualifiedName(column);
		var matches = descriptions(connection).stream().filter(type -> type.qualifiedName().equals(name)).toList();
		if (matches.isEmpty())
			matches = refresh(connection).stream().filter(type -> type.qualifiedName().equals(name)).toList();
		if (matches.size() != 1)
			throw new IllegalArgumentException(
					"Create the destination Oracle interval collection type before use: " + name);
		var actual = matches.getFirst();
		int fraction = column.getScale() == null ? 6 : column.getScale();
		if (column.getDataType() != actual.element() || column.getLength() == null
				|| column.getLength() != actual.leading()
				|| actual.element() == DataType.INTERVAL_DAY_TO_SECOND && fraction != actual.fraction()
				|| !actual.kind().equals(column.getSpecifics().get(KIND))
				|| !Objects.equals(actual.bound() == null ? null : actual.bound().toString(),
						column.getSpecifics().get(IntervalArrays.DECLARED_BOUNDS)))
			throw new IllegalArgumentException(
					"Oracle collection element precision or cardinality changed; resolve the column again: " + name);
		return actual;
	}
}
