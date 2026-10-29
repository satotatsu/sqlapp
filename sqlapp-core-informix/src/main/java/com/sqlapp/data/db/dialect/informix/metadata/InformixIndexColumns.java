/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Parses INDEXKEYARRAY's catalog representation, including multi-argument UDR
 * keys.
 */
final class InformixIndexColumns {
	private static final Pattern KEY = Pattern
			.compile("(?:<(\\d+)>\\(\\s*(-?\\d+(?:\\s*,\\s*-?\\d+)*)\\s*\\)|(-?\\d+))\\s*\\[(\\d+)\\]");
	private final List<Key> keys = new ArrayList<>();

	record Routine(String schema, String name) {
	}

	record OperatorClass(int accessMethod, String schema, String name) {
	}

	private record Key(Integer routine, List<Integer> columns, Order order, int operatorClass) {
	}

	InformixIndexColumns(final String rawKeys) {
		if (rawKeys == null || rawKeys.isBlank()) {
			throw new IllegalArgumentException("Missing Informix INDEXKEYARRAY definition");
		}
		int position = 0;
		while (position < rawKeys.length()) {
			while (position < rawKeys.length() && Character.isWhitespace(rawKeys.charAt(position)))
				position++;
			final var matcher = KEY.matcher(rawKeys).region(position, rawKeys.length());
			if (!matcher.lookingAt()) {
				throw new UnsupportedOperationException(
						"Unsupported Informix index key at position " + position + ": " + rawKeys);
			}
			final int operatorClass = Integer.parseInt(matcher.group(4));
			if (operatorClass <= 0)
				throw new IllegalArgumentException("Invalid Informix operator class: " + operatorClass);
			final Integer routine = matcher.group(1) == null ? null : Integer.valueOf(matcher.group(1));
			final String[] numbers = (routine == null ? matcher.group(3) : matcher.group(2)).split(",");
			final List<Integer> columns = new ArrayList<>();
			Order order = Order.Asc;
			for (int i = 0; i < numbers.length; i++) {
				final int number = Integer.parseInt(numbers[i].trim());
				if (number == 0 || number == Integer.MIN_VALUE || i > 0 && number < 0) {
					throw new UnsupportedOperationException("Invalid Informix index column number: " + number);
				}
				if (i == 0 && number < 0)
					order = Order.Desc;
				columns.add(Math.abs(number));
			}
			keys.add(new Key(routine, columns, order, operatorClass));
			position = matcher.end();
			while (position < rawKeys.length() && Character.isWhitespace(rawKeys.charAt(position)))
				position++;
			if (position < rawKeys.length()) {
				if (rawKeys.charAt(position++) != ',' || rawKeys.substring(position).isBlank()) {
					throw new UnsupportedOperationException("Invalid Informix index key separator: " + rawKeys);
				}
			}
		}
	}

	void routineIds(final Set<Integer> ids) {
		keys.stream().map(Key::routine).filter(java.util.Objects::nonNull).forEach(ids::add);
	}

	boolean containsColumn(int number) {
		return keys.stream().anyMatch(key -> key.routine() == null && key.columns().contains(number));
	}

	void operatorClassIds(final Set<Integer> ids, final int accessMethod) {
		keys.stream().map(Key::operatorClass).filter(id -> id != 1 || accessMethod != 1).forEach(ids::add);
	}

	void apply(final Index index, final Map<Integer, String> columns, final Map<Integer, Routine> routines,
			final Map<Integer, OperatorClass> operatorClasses, final int accessMethod,
			final AbstractSqlBuilder<?> builder) {
		for (final Key key : keys) {
			if (key.routine() == null) {
				final var reference = index.getColumns().add(column(index, columns, key.columns().get(0)),
						c -> c.setOrder(key.order()));
				operatorClass(index, reference, key, operatorClasses, accessMethod);
				continue;
			}
			final Routine routine = routines.get(key.routine());
			if (routine == null) {
				throw new IllegalStateException(
						"Cannot resolve Informix index routine " + key.routine() + " for " + index.getName());
			}
			if (index.getIndexType() == IndexType.Clustered)
				index.getSpecifics().put(InformixIndexReader.INFORMIX_CLUSTERED, "true");
			index.setIndexType(IndexType.Function);
			final var expression = builder.clone();
			expression.name(routine.schema())._add('.').name(routine.name())._add('(');
			for (int i = 0; i < key.columns().size(); i++) {
				expression.comma(i > 0).name(column(index, columns, key.columns().get(i)));
			}
			expression._add(')');
			final var reference = index.getColumns().add(expression.toString(), c -> {
				c.setOrder(key.order());
				c.getSpecifics().put(InformixIndexReader.INFORMIX_INDEX_EXPRESSION, "true");
			});
			operatorClass(index, reference, key, operatorClasses, accessMethod);
		}
	}

	private void operatorClass(final Index index, final com.sqlapp.data.schemas.ReferenceColumn reference,
			final Key key, final Map<Integer, OperatorClass> classes, final int accessMethod) {
		if (key.operatorClass() == 1 && accessMethod == 1)
			return;
		final OperatorClass operatorClass = classes.get(key.operatorClass());
		if (operatorClass == null || operatorClass.schema() == null || operatorClass.schema().isBlank()
				|| operatorClass.name() == null || operatorClass.name().isBlank()) {
			throw new IllegalStateException(
					"Cannot resolve Informix operator class " + key.operatorClass() + " for " + index.getName());
		}
		if (operatorClass.accessMethod() != accessMethod) {
			throw new IllegalStateException(
					"Informix operator class does not belong to the index access method: " + index.getName());
		}
		reference.getSpecifics().put(InformixIndexReader.INFORMIX_OPERATOR_CLASS, operatorClass.name());
		reference.getSpecifics().put(InformixIndexReader.INFORMIX_OPERATOR_CLASS_OWNER, operatorClass.schema());
	}

	private String column(final Index index, final Map<Integer, String> columns, final int number) {
		final String name = columns.get(number);
		if (name == null) {
			throw new IllegalStateException(
					"Cannot resolve Informix index column " + number + " for " + index.getCatalogName() + "."
							+ index.getSchemaName() + "." + index.getTableName() + "." + index.getName());
		}
		return name;
	}
}
