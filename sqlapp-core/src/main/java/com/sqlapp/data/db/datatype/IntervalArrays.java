/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.datatype;

import java.lang.reflect.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Exact recursive interval arrays; JDBC leaves are read as text, never narrow driver interval objects. */
public final class IntervalArrays {
	public static final String DECLARED_BOUNDS = "interval.arrayBounds";
	private IntervalArrays() {}

	public static int dimensions(Object value) {
		if (value == null) return 0;
		if (!(value instanceof List<?>) && !value.getClass().isArray()) return 0;
		int size = value instanceof List<?> list ? list.size() : Array.getLength(value);
		int child = 0;
		for (int i = 0; i < size; i++)
			child = Math.max(child, dimensions(value instanceof List<?> list ? list.get(i) : Array.get(value, i)));
		if (child >= 32) throw new IllegalArgumentException("Too many interval array dimensions");
		return 1 + child;
	}

	public static Object map(Object value, int dimensions, Function<Object, Object> leaf) {
		if (value == null) return null;
		if (dimensions == 0) {
			if (value.getClass().isArray() || value instanceof List<?>)
				throw new IllegalArgumentException("Interval array has too many dimensions");
			return leaf.apply(value);
		}
		if (dimensions < 0 || dimensions > 32) throw new IllegalArgumentException("Interval array dimensions must be 0..32");
		int size;
		if (value instanceof List<?> list) size = list.size();
		else if (value.getClass().isArray()) size = Array.getLength(value);
		else throw new IllegalArgumentException("Interval array dimensions do not match Schema");
		Object[] result = new Object[size];
		for (int i = 0; i < size; i++)
			result[i] = map(value instanceof List<?> list ? list.get(i) : Array.get(value, i), dimensions - 1, leaf);
		return result;
	}

	public static Object read(java.sql.Array array, Function<String, Object> decoder) throws SQLException {
		if (array == null) return null;
		try (var values = array.getResultSet()) {
			var result = new ArrayList<Object>();
			boolean nested = values.getMetaData().getColumnType(2) == Types.ARRAY;
			while (values.next()) {
				String text = nested ? null : values.getString(2);
				result.add(nested ? read(values.getArray(2), decoder) : text == null ? null : decoder.apply(text));
			}
			return result.toArray();
		} finally { array.free(); }
	}

	/** PostgreSQL requires rectangular dimensions and cannot store null subarrays. */
	public static int[] rectangular(Object value, int dimensions) {
		if (dimensions == 0) return new int[0];
		if (value == null || !value.getClass().isArray())
			throw new IllegalArgumentException("Array requires non-null rectangular subarrays");
		int size = Array.getLength(value);
		int[] shape = new int[dimensions];
		shape[0] = size;
		int[] previous = null;
		for (int i = 0; i < size && dimensions > 1; i++) {
			int[] child = rectangular(Array.get(value, i), dimensions - 1);
			if (child[0] == 0 || previous != null && !java.util.Arrays.equals(previous, child))
				throw new IllegalArgumentException("Array requires nonempty rectangular subarrays");
			previous = child;
		}
		if (previous != null) System.arraycopy(previous, 0, shape, 1, previous.length);
		return shape;
	}

	/** Bounds are listed in SQL declaration order (innermost first); '*' means unspecified. */
	public static void validateBounds(Object value, int dimensions, String declaration) {
		if (declaration == null || value == null) return;
		String[] bounds = declaration.split(",", -1);
		if (bounds.length != dimensions) throw new IllegalArgumentException("Interval array bounds do not match dimensions");
		validateBounds(value, dimensions, bounds);
	}

	private static void validateBounds(Object value, int dimensions, String[] bounds) {
		if (value == null) return;
		int size = value instanceof List<?> list ? list.size() : Array.getLength(value);
		String bound = bounds[dimensions - 1];
		if (!bound.equals("*") && size > Integer.parseInt(bound))
			throw new IllegalArgumentException("Interval array exceeds declared cardinality");
		if (dimensions > 1)
			for (int i = 0; i < size; i++)
				validateBounds(value instanceof List<?> list ? list.get(i) : Array.get(value, i), dimensions - 1, bounds);
	}

	public static boolean array(ResultSet rows, int index) throws SQLException {
		var metadata = rows.getMetaData();
		return metadata != null && metadata.getColumnType(index) == Types.ARRAY;
	}

	public static String literal(Object value, int dimensions, Function<Object, String> leaf) {
		if (value == null) return "NULL";
		if (dimensions == 0) return leaf.apply(value);
		Object[] elements;
		if (value instanceof List<?> list) elements = list.toArray();
		else if (value.getClass().isArray()) {
			elements = new Object[Array.getLength(value)];
			for (int i = 0; i < elements.length; i++) elements[i] = Array.get(value, i);
		} else throw new IllegalArgumentException("Interval array dimensions do not match Schema");
		var strings = new ArrayList<String>();
		for (var element : elements) strings.add(literal(element, dimensions - 1, leaf));
		return "ARRAY[" + String.join(",", strings) + "]";
	}
}
