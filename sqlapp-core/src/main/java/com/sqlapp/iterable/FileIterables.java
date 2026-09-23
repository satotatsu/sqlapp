
/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.iterable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import com.sqlapp.data.schemas.rowiterator.DataFormat;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;

public class FileIterables {

	public static boolean supports(File file) {
		return file != null && supports(DataFormat.parse(file));
	}

	public static boolean supports(Path path) {
		return path != null && supports(DataFormat.parse(path));
	}

	private static boolean supports(DataFormat format) {
		return format != null && format.supportsMapRows();
	}

	public static Iterable<Map<String, Object>> readAsMap(Path p) {
		return requireMapFormat(p).createMapIterable(p);
	}

	public static Iterable<Map<String, Object>> readAsMap(File p) {
		return requireMapFormat(p).createMapIterable(p);
	}

	private static DataFormat requireMapFormat(Path path) {
		final DataFormat format = path == null ? null : DataFormat.parse(path);
		if (!supports(format)) {
			throw new IllegalArgumentException("Unsupported data file format: " + path);
		}
		return format;
	}

	private static DataFormat requireMapFormat(File file) {
		final DataFormat format = file == null ? null : DataFormat.parse(file);
		if (!supports(format)) {
			throw new IllegalArgumentException("Unsupported data file format: " + file);
		}
		return format;
	}

	private static Iterable<Map<String, Object>> readSupportedAsMap(Path path) {
		return supports(path) ? readAsMap(path) : Collections.emptyList();
	}

	private static Iterable<Map<String, Object>> readSupportedAsMap(File file) {
		return supports(file) ? readAsMap(file) : Collections.emptyList();
	}

	public static List<Iterable<Map<String, Object>>> readAllAsMap(Path pathObj, Predicate<Path> filter) {
		return readAllInternalAsMap(pathObj, p -> Files.isRegularFile(p), filter, p -> readSupportedAsMap(p),
				p -> com.sqlapp.util.FileUtils.list(p, f -> true));
	}

	public static List<Iterable<Map<String, Object>>> readAllRecursiveAsMap(Path pathObj, Predicate<Path> filter) {
		return readAllInternalAsMap(pathObj, p -> Files.isRegularFile(p), filter, p -> readSupportedAsMap(p),
				p -> com.sqlapp.util.FileUtils.walk(p, f -> true));
	}

	public static List<Iterable<Map<String, Object>>> readAllAsMap(File pathObj, Predicate<File> filter) {
		return readAllInternalAsMap(pathObj, p -> p.isFile(), filter, p -> readSupportedAsMap(p),
				p -> com.sqlapp.util.FileUtils.list(p, f -> true));
	}

	public static List<Iterable<Map<String, Object>>> readAllRecursiveAsMap(File pathObj, Predicate<File> filter) {
		return readAllInternalAsMap(pathObj, p -> p.isFile(), filter, p -> readSupportedAsMap(p),
				p -> com.sqlapp.util.FileUtils.walk(p, f -> true));
	}

	public static List<Iterable<Map<String, Object>>> readAllAsMap(Path pathObj, String filterExpression,
			CachedMvelEvaluator cmvelEvaluator) {
		return readAllInternalAsMap(pathObj, p -> Files.isRegularFile(p), filterExpression, cmvelEvaluator,
				p -> readSupportedAsMap(p), p -> com.sqlapp.util.FileUtils.list(p, f -> true));
	}

	public static List<Iterable<Map<String, Object>>> readAllAsMap(File pathObj, String filterExpression,
			CachedMvelEvaluator cmvelEvaluator) {
		return readAllInternalAsMap(pathObj, p -> p.isFile(), filterExpression, cmvelEvaluator,
				p -> readSupportedAsMap(p),
				p -> com.sqlapp.util.FileUtils.list(p, f -> true));
	}

	public static List<Iterable<Map<String, Object>>> readAllRecursiveAsMap(Path pathObj, String filterExpression,
			CachedMvelEvaluator cmvelEvaluator) {
		return readAllInternalAsMap(pathObj, p -> Files.isRegularFile(p), filterExpression, cmvelEvaluator,
				p -> readSupportedAsMap(p), p -> com.sqlapp.util.FileUtils.walk(p, f -> true));
	}

	public static List<Iterable<Map<String, Object>>> readAllRecursiveAsMap(File pathObj, String filterExpression,
			CachedMvelEvaluator cmvelEvaluator) {
		return readAllInternalAsMap(pathObj, p -> p.isFile(), filterExpression, cmvelEvaluator,
				p -> readSupportedAsMap(p),
				p -> com.sqlapp.util.FileUtils.walk(p, f -> true));
	}

	private static <T> List<Iterable<Map<String, Object>>> readAllInternalAsMap(T pathObj, Predicate<T> filePredicate,
			Predicate<T> fileFilter, Function<T, Iterable<Map<String, Object>>> readerConverter,
			IOExceptionFunction<T, List<T>> walkFunction) {
		if (filePredicate.test(pathObj)) {
			if (fileFilter.test(pathObj)) {
				Iterable<Map<String, Object>> itr = readerConverter.apply(pathObj);
				if (Collections.emptyList().equals(itr)) {
					return Collections.emptyList();
				} else {
					final List<Iterable<Map<String, Object>>> result = CommonUtils.list();
					result.add(itr);
					return result;
				}
			}
			return Collections.emptyList();
		}
		final List<Iterable<Map<String, Object>>> result = CommonUtils.list();
		try {
			final List<T> pathList = walkFunction.apply(pathObj);
			for (T p : pathList) {
				if (!fileFilter.test(p)) {
					continue;
				}
				Iterable<Map<String, Object>> itr = readerConverter.apply(p);
				if (!Collections.emptyList().equals(itr)) {
					result.add(itr);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return result;
	}

	private static <T> List<Iterable<Map<String, Object>>> readAllInternalAsMap(T pathObj, Predicate<T> filePredicate,
			String filterExpression, CachedMvelEvaluator cmvelEvaluator,
			Function<T, Iterable<Map<String, Object>>> readerConverter, IOExceptionFunction<T, List<T>> walkFunction) {
		if (filePredicate.test(pathObj)) {
			Map<String, Object> map = CommonUtils.map();
			map.put("file", pathObj);
			boolean bool = cmvelEvaluator.evalBoolean(filterExpression, map);
			if (bool) {
				Iterable<Map<String, Object>> itr = readerConverter.apply(pathObj);
				if (Collections.emptyList().equals(itr)) {
					return Collections.emptyList();
				} else {
					final List<Iterable<Map<String, Object>>> result = CommonUtils.list();
					result.add(itr);
					return result;
				}
			}
			return Collections.emptyList();
		}
		final List<Iterable<Map<String, Object>>> result = CommonUtils.list();
		try {
			final List<T> pathList = walkFunction.apply(pathObj);
			for (T p : pathList) {
				Map<String, Object> map = CommonUtils.map();
				map.put("file", p);
				boolean bool = cmvelEvaluator.evalBoolean(filterExpression, map);
				if (!bool) {
					continue;
				}
				Iterable<Map<String, Object>> itr = readerConverter.apply(p);
				if (!Collections.emptyList().equals(itr)) {
					result.add(itr);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return result;
	}

	@FunctionalInterface
	public interface IOExceptionFunction<T, S> {
		S apply(T t) throws IOException;
	}

}
