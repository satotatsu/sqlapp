/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.generator.util;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.schemas.rowiterator.DataFormat;
import com.sqlapp.iterable.CombinedFileIterable;
import com.sqlapp.iterable.FileIterables;
import com.sqlapp.iterable.MapIterable;
import com.sqlapp.util.iterator.Iterators;

public final class GeneratorMvelUtils {

	public static Iterable<Map<String, Object>> iterator(long iterate) {
		return new MapIterable(Iterators.range(iterate));
	}

	public static Iterable<Map<String, Object>> fileIterator(Object path) {
		return fileIterator(path, false, null);
	}

	public static Iterable<Map<String, Object>> fileIterator(Object path, String filterExpression) {
		return fileIterator(path, false, filterExpression);
	}

	public static Iterable<Map<String, Object>> fileIteratorRecursive(Object path) {
		return fileIterator(path, true, null);
	}

	public static Iterable<Map<String, Object>> fileIteratorRecursive(Object path, String filterExpression) {
		return fileIterator(path, true, filterExpression);
	}

	private static Iterable<Map<String, Object>> fileIterator(final Object path, final boolean recursive,
			final String filterExpression) {
		final List<Iterable<Map<String, Object>>> iterables;
		if (path instanceof Path value) {
			iterables = read(value, recursive, filterExpression);
		} else if (path instanceof File value) {
			iterables = read(value, recursive, filterExpression);
		} else if (path instanceof String value) {
			iterables = read(new File(value), recursive, filterExpression);
		} else {
			return iterator(0);
		}
		return new CombinedFileIterable<>(iterables);
	}

	private static List<Iterable<Map<String, Object>>> read(final File path, final boolean recursive,
			final String filterExpression) {
		validatePath(path.toPath());
		if (filterExpression == null) {
			return recursive ? FileIterables.readAllRecursiveAsMap(path, f -> true)
					: FileIterables.readAllAsMap(path, f -> true);
		}
		return recursive
				? FileIterables.readAllRecursiveAsMap(path, filterExpression,
						CachedMvelEvaluatorUtils.getCachedMvelEvaluator())
				: FileIterables.readAllAsMap(path, filterExpression,
						CachedMvelEvaluatorUtils.getCachedMvelEvaluator());
	}

	private static List<Iterable<Map<String, Object>>> read(final Path path, final boolean recursive,
			final String filterExpression) {
		validatePath(path);
		if (filterExpression == null) {
			return recursive ? FileIterables.readAllRecursiveAsMap(path, f -> true)
					: FileIterables.readAllAsMap(path, f -> true);
		}
		return recursive
				? FileIterables.readAllRecursiveAsMap(path, filterExpression,
						CachedMvelEvaluatorUtils.getCachedMvelEvaluator())
				: FileIterables.readAllAsMap(path, filterExpression,
						CachedMvelEvaluatorUtils.getCachedMvelEvaluator());
	}

	private static void validatePath(final Path path) {
		if (!java.nio.file.Files.exists(path)) {
			throw new IllegalArgumentException("Data path does not exist: " + path);
		}
		if (!java.nio.file.Files.isRegularFile(path)) {
			return;
		}
		final DataFormat format = DataFormat.parse(path);
		if (format == null || format.isToml()) {
			throw new IllegalArgumentException("Unsupported data file format: " + path);
		}
	}

}
