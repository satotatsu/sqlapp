package com.sqlapp.data.db.command.generator.util;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import com.sqlapp.iterable.CombinedFileIterable;
import com.sqlapp.iterable.FileIterables;
import com.sqlapp.iterable.MapIterable;
import com.sqlapp.util.iterator.Iterators;

public final class GeneratorMvelUtils {

	public static Iterable<Map<String, Object>> iterator(long iterate) {
		return new MapIterable(Iterators.range(iterate));
	}

	public static Iterable<Map<String, Object>> fileIterator(Object path) {
		if (path instanceof File) {
			Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(
					FileIterables.readAllAsMap((File) path, f -> true));
			return iterable;
		} else if (path instanceof Path) {
			Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(
					FileIterables.readAllAsMap((Path) path, f -> true));
			return iterable;
		} else if (path instanceof String) {
			Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(
					FileIterables.readAllAsMap(new File((String) path), f -> true));
			return iterable;
		}
		return iterator(0);
	}

	public static Iterable<Map<String, Object>> fileIterator(Object path, String filterExpression) {
		if (path instanceof File) {
			Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(FileIterables
					.readAllAsMap((File) path, filterExpression, CachedMvelEvaluatorUtils.getCachedMvelEvaluator()));
			return iterable;
		} else if (path instanceof Path) {
			Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(FileIterables
					.readAllAsMap((Path) path, filterExpression, CachedMvelEvaluatorUtils.getCachedMvelEvaluator()));
			return iterable;
		} else if (path instanceof String) {
			Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(
					FileIterables.readAllAsMap(new File((String) path), filterExpression,
							CachedMvelEvaluatorUtils.getCachedMvelEvaluator()));
			return iterable;
		}
		return iterator(0);
	}

	public static Iterable<Map<String, Object>> fileIteratorRecursive(Object path) {
		if (path instanceof File) {
			Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(
					FileIterables.readAllRecursiveAsMap((File) path, f -> true));
			return iterable;
		} else if (path instanceof Path) {
			Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(
					FileIterables.readAllRecursiveAsMap((Path) path, f -> true));
			return iterable;
		} else if (path instanceof String) {
			Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(
					FileIterables.readAllRecursiveAsMap(new File((String) path), f -> true));
			return iterable;
		}
		return iterator(0);
	}

	public static Iterable<Map<String, Object>> fileIteratorRecursive(Object path, String filterExpression) {
		if (path instanceof File) {
			Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(
					FileIterables.readAllRecursiveAsMap((File) path, filterExpression,
							CachedMvelEvaluatorUtils.getCachedMvelEvaluator()));
			return iterable;
		} else if (path instanceof Path) {
			Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(
					FileIterables.readAllRecursiveAsMap((Path) path, filterExpression,
							CachedMvelEvaluatorUtils.getCachedMvelEvaluator()));
			return iterable;
		} else if (path instanceof String) {
			Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(
					FileIterables.readAllRecursiveAsMap(new File((String) path), filterExpression,
							CachedMvelEvaluatorUtils.getCachedMvelEvaluator()));
			return iterable;
		}
		return iterator(0);
	}

}
