
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

import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.util.CommonUtils;

public class FileIterables {

	public static Iterable<Map<String, Object>> readAsMap(Path p) {
		return readAsMapInternal(p, () -> WorkbookFileType.parse(p), (type) -> type.createMapIterable(p),
				() -> createMapIterableFromXml(p));
	}

	public static Iterable<Map<String, Object>> readAsMap(File p) {
		return readAsMapInternal(p, () -> WorkbookFileType.parse(p), (type) -> type.createMapIterable(p),
				() -> createMapIterableFromXml(p));
	}

	private static <T> Iterable<Map<String, Object>> readAsMapInternal(T p, Supplier<WorkbookFileType> func,
			Function<WorkbookFileType, Iterable<Map<String, Object>>> convertByWorkbook,
			Supplier<Iterable<Map<String, Object>>> convertByXml) {
		WorkbookFileType type = func.get();
		if (type == null) {
			return Collections.emptyList();
		}
		Iterable<Map<String, Object>> itr = convertByWorkbook.apply(type);
		if (itr != null) {
			return itr;
		}
		if (type == WorkbookFileType.XML) {
			itr = convertByXml.get();
			return itr;
		}
		return Collections.emptyList();
	}

	public static List<Iterable<Map<String, Object>>> readAllAsMap(Path pathObj, Predicate<Path> filter) {
		return readAllInternalAsMap(pathObj, p -> Files.isRegularFile(p), filter, p -> readAsMap(p),
				p -> com.sqlapp.util.FileUtils.list(p, filter));
	}

	public static List<Iterable<Map<String, Object>>> readAllRecursiveAsMap(Path pathObj, Predicate<Path> filter) {
		return readAllInternalAsMap(pathObj, p -> Files.isRegularFile(p), filter, p -> readAsMap(p),
				p -> com.sqlapp.util.FileUtils.walk(p, filter));
	}

	public static List<Iterable<Map<String, Object>>> readAllAsMap(File pathObj, Predicate<File> filter) {
		return readAllInternalAsMap(pathObj, p -> p.isFile(), filter, p -> readAsMap(p),
				p -> com.sqlapp.util.FileUtils.list(p, filter));
	}

	public static List<Iterable<Map<String, Object>>> readAllRecursiveAsMap(File pathObj, Predicate<File> filter) {
		return readAllInternalAsMap(pathObj, p -> p.isFile(), filter, p -> readAsMap(p),
				p -> com.sqlapp.util.FileUtils.walk(p, filter));
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

	private static Iterable<Map<String, Object>> createMapIterableFromXml(File file) {
		return new XmlRowIterable(file);
	}

	private static Iterable<Map<String, Object>> createMapIterableFromXml(Path path) {
		return new XmlRowIterable(path);
	}

	@FunctionalInterface
	public interface IOExceptionFunction<T, S> {
		S apply(T t) throws IOException;
	}

}
