/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.LinkedHashMap;
import java.util.Map;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.AbstractDbObject;

/**
 * Oracle schema annotation support backed by Schema object specifics.
 */
public final class OracleAnnotationUtils {

	public static final String PREFIX = "ANNOTATION.";

	private OracleAnnotationUtils() {
	}

	public static boolean isAnnotationKey(final String key) {
		return key != null && key.regionMatches(true, 0, PREFIX, 0,
				PREFIX.length());
	}

	public static Map<String, String> getAnnotations(
			final AbstractDbObject<?> object) {
		final Map<String, String> result = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry
				: object.getSpecifics().entrySet()) {
			if (isAnnotationKey(entry.getKey())) {
				result.put(entry.getKey().substring(PREFIX.length()),
						entry.getValue());
			}
		}
		return result;
	}

	public static void setAnnotation(final AbstractDbObject<?> object,
			final String name, final String value) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException(
					"annotation name must not be empty");
		}
		if (name.length() > 1024) {
			throw new IllegalArgumentException(
					"annotation name must not exceed 1024 characters");
		}
		if (value != null && value.length() > 4000) {
			throw new IllegalArgumentException(
					"annotation value must not exceed 4000 characters");
		}
		object.getSpecifics().put(PREFIX + name,
				value == null ? "" : value);
	}

	public static void addAnnotations(final OracleSqlBuilder builder,
			final AbstractDbObject<?> object) {
		final Map<String, String> annotations = getAnnotations(object);
		if (annotations.isEmpty()) {
			return;
		}
		builder.space()._add("ANNOTATIONS").space()._add("(");
		int i = 0;
		for (Map.Entry<String, String> entry : annotations.entrySet()) {
			builder.comma(i > 0).name(entry.getKey());
			if (entry.getValue() != null
					&& !entry.getValue().isEmpty()) {
				builder.space().sqlChar(entry.getValue());
			}
			i++;
		}
		builder._add(")");
	}
}
