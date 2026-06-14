
package com.sqlapp.iterable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.util.CommonUtils;

public class Iterables {

	public static Iterable<Map<String, Object>> readAsMap(Path p) {
		WorkbookFileType type = WorkbookFileType.parse(p);
		Iterable<Map<String, Object>> itr = type.createMapIterable(p);
		if (itr != null) {
			return itr;
		}
		return Collections.emptyList();
	}

	public static Iterable<Map<String, Object>> readAsMap(File p) {
		WorkbookFileType type = WorkbookFileType.parse(p);
		Iterable<Map<String, Object>> itr = type.createMapIterable(p);
		if (itr != null) {
			return itr;
		}
		return Collections.emptyList();
	}

	public static List<Iterable<Map<String, Object>>> readAllAsMap(Path pathObj, Predicate<Path> filter) {
		return readAllInternalAsMap(pathObj, p -> java.nio.file.Files.list(pathObj), filter);
	}

	public static List<Iterable<Map<String, Object>>> readAllRecursiveAsMap(Path pathObj, Predicate<Path> filter) {
		return readAllInternalAsMap(pathObj, p -> java.nio.file.Files.walk(pathObj), filter);
	}

	private static List<Iterable<Map<String, Object>>> readAllInternalAsMap(Path pathObj,
			IOExceptionFunction<Path, Stream<Path>> walkFunction, Predicate<Path> filter) {
		if (Files.isRegularFile(pathObj)) {
			Iterable<Map<String, Object>> itr = readAsMap(pathObj);
			if (itr.equals(Collections.emptyList())) {
				return Collections.emptyList();
			} else {
				final List<Iterable<Map<String, Object>>> result = CommonUtils.list();
				result.add(itr);
				return result;
			}
		}
		final List<Iterable<Map<String, Object>>> result = CommonUtils.list();
		try (Stream<Path> paths = walkFunction.apply(pathObj)) {
			final List<Path> pathList = paths.filter(Files::isRegularFile).filter(filter).collect(Collectors.toList());
			for (Path p : pathList) {
				WorkbookFileType type = WorkbookFileType.parse(pathObj);
				if (type == null) {
					continue;
				}
				Iterable<Map<String, Object>> itr = type.createMapIterable(p);
				if (itr != null) {
					result.add(itr);
				}
			}
			return result;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static List<Iterable<Map<String, Object>>> readAllAsMap(File pathObj, Predicate<File> filter) {
		return readAllInternalAsMap(pathObj, p -> com.sqlapp.util.FileUtils.list(pathObj, filter));
	}

	public static List<Iterable<Map<String, Object>>> readAllRecursiveAsMap(File pathObj, Predicate<File> filter) {
		return readAllInternalAsMap(pathObj, p -> com.sqlapp.util.FileUtils.walk(pathObj, filter));
	}

	private static List<Iterable<Map<String, Object>>> readAllInternalAsMap(File pathObj,
			IOExceptionFunction<File, List<File>> walkFunction) {
		if (pathObj.isFile()) {
			final Iterable<Map<String, Object>> itr = readAsMap(pathObj);
			if (itr.equals(Collections.emptyList())) {
				return Collections.emptyList();
			} else {
				final List<Iterable<Map<String, Object>>> result = CommonUtils.list();
				result.add(itr);
				return result;
			}
		}
		final List<Iterable<Map<String, Object>>> result = CommonUtils.list();
		try {
			final List<File> pathList = walkFunction.apply(pathObj);
			for (File p : pathList) {
				WorkbookFileType type = WorkbookFileType.parse(pathObj);
				if (type == null) {
					continue;
				}
				final Iterable<Map<String, Object>> itr = type.createMapIterable(p);
				if (itr != null) {
					result.add(itr);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return result;
	}

	private Iterable<Map<String, Object>> createMapIterableFromXml(File file) {
		return new XmlRowIterable(file);
	}

	private Iterable<Map<String, Object>> createMapIterableFromXml(Path path) {
		return new XmlRowIterable(path);
	}

	private Iterable<Map<String, Object>> createMapIterableFromXml(InputStream is) {
		return new XmlRowIterable(is);
	}

	private Iterable<Map<String, Object>> createMapIterableFromXml(Reader reader) {
		return new XmlRowIterable(reader);
	}

	@FunctionalInterface
	public interface IOExceptionFunction<T, S> {
		S apply(T t) throws IOException;
	}

}
