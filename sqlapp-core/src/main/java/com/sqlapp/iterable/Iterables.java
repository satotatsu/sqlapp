package com.sqlapp.iterable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;

public class Iterables {

	public static MapIterable read(String path) {
		Path pathObj = Paths.get(path);
		try (Stream<Path> paths = Files.walk(pathObj)) {
			List<Path> filePaths = paths.filter(Files::isRegularFile).filter(p -> {
				WorkbookFileType type = WorkbookFileType.parse(p);
				if (type == null) {
					return false;
				}
				if (type.isCsv()) {
					return true;
				}
				if (type.isYaml()) {
					return true;
				}
				return false;
			}).collect(Collectors.toList());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}
}
