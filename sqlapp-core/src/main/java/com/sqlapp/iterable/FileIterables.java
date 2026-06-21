
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
import java.util.function.Supplier;

import com.sqlapp.data.schemas.rowiterator.DataFormat;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;

public class FileIterables {

	public static Iterable<Map<String, Object>> readAsMap(Path p) {
		return readAsMapInternal(p, () -> DataFormat.parse(p), (type) -> type.createMapIterable(p),
				() -> createMapIterableFromXml(p), () -> createMapIterableFromExcel(p));
	}

	public static Iterable<Map<String, Object>> readAsMap(File p) {
		return readAsMapInternal(p, () -> DataFormat.parse(p), (type) -> type.createMapIterable(p),
				() -> createMapIterableFromXml(p), () -> createMapIterableFromExcel(p));
	}

	private static <T> Iterable<Map<String, Object>> readAsMapInternal(T p, Supplier<DataFormat> func,
			Function<DataFormat, Iterable<Map<String, Object>>> convertByWorkbook,
			Supplier<Iterable<Map<String, Object>>> convertByXml,
			Supplier<Iterable<Map<String, Object>>> convertByExcel) {
		DataFormat type = func.get();
		if (type == null) {
			return Collections.emptyList();
		}
		Iterable<Map<String, Object>> itr = convertByWorkbook.apply(type);
		if (itr != null) {
			return itr;
		}
		if (type == DataFormat.XML) {
			itr = convertByXml.get();
			return itr;
		}
		if (type == DataFormat.EXCEL) {
			itr = convertByExcel.get();
			return itr;
		}
		return Collections.emptyList();
	}

	public static List<Iterable<Map<String, Object>>> readAllAsMap(Path pathObj, Predicate<Path> filter) {
		return readAllInternalAsMap(pathObj, p -> Files.isRegularFile(p), filter, p -> readAsMap(p),
				p -> com.sqlapp.util.FileUtils.list(p, f -> true));
	}

	public static List<Iterable<Map<String, Object>>> readAllRecursiveAsMap(Path pathObj, Predicate<Path> filter) {
		return readAllInternalAsMap(pathObj, p -> Files.isRegularFile(p), filter, p -> readAsMap(p),
				p -> com.sqlapp.util.FileUtils.walk(p, f -> true));
	}

	public static List<Iterable<Map<String, Object>>> readAllAsMap(File pathObj, Predicate<File> filter) {
		return readAllInternalAsMap(pathObj, p -> p.isFile(), filter, p -> readAsMap(p),
				p -> com.sqlapp.util.FileUtils.list(p, f -> true));
	}

	public static List<Iterable<Map<String, Object>>> readAllRecursiveAsMap(File pathObj, Predicate<File> filter) {
		return readAllInternalAsMap(pathObj, p -> p.isFile(), filter, p -> readAsMap(p),
				p -> com.sqlapp.util.FileUtils.walk(p, f -> true));
	}

	public static List<Iterable<Map<String, Object>>> readAllAsMap(Path pathObj, String filterExpression,
			CachedMvelEvaluator cmvelEvaluator) {
		return readAllInternalAsMap(pathObj, p -> Files.isRegularFile(p), filterExpression, cmvelEvaluator,
				p -> readAsMap(p), p -> com.sqlapp.util.FileUtils.list(p, f -> true));
	}

	public static List<Iterable<Map<String, Object>>> readAllAsMap(File pathObj, String filterExpression,
			CachedMvelEvaluator cmvelEvaluator) {
		return readAllInternalAsMap(pathObj, p -> p.isFile(), filterExpression, cmvelEvaluator, p -> readAsMap(p),
				p -> com.sqlapp.util.FileUtils.list(p, f -> true));
	}

	public static List<Iterable<Map<String, Object>>> readAllRecursiveAsMap(Path pathObj, String filterExpression,
			CachedMvelEvaluator cmvelEvaluator) {
		return readAllInternalAsMap(pathObj, p -> Files.isRegularFile(p), filterExpression, cmvelEvaluator,
				p -> readAsMap(p), p -> com.sqlapp.util.FileUtils.list(p, f -> true));
	}

	public static List<Iterable<Map<String, Object>>> readAllRecursiveAsMap(File pathObj, String filterExpression,
			CachedMvelEvaluator cmvelEvaluator) {
		return readAllInternalAsMap(pathObj, p -> p.isFile(), filterExpression, cmvelEvaluator, p -> readAsMap(p),
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
				if (!fileFilter.test(pathObj)) {
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

	private static Iterable<Map<String, Object>> createMapIterableFromXml(File file) {
		return new XmlRowIterable(file);
	}

	private static Iterable<Map<String, Object>> createMapIterableFromXml(Path path) {
		return new XmlRowIterable(path);
	}

	private static Iterable<Map<String, Object>> createMapIterableFromExcel(File file) {
		return new ExcelIterable(file);
	}

	private static Iterable<Map<String, Object>> createMapIterableFromExcel(Path path) {
		return new ExcelIterable(path);
	}

	@FunctionalInterface
	public interface IOExceptionFunction<T, S> {
		S apply(T t) throws IOException;
	}

}
