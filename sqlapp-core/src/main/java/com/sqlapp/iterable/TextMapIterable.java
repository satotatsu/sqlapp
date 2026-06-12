package com.sqlapp.iterable;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;

public class TextMapIterable extends AbstractTextMapIterable {

	public TextMapIterable(File file) {
		super(file);
	}

	public TextMapIterable(Path path) {
		super(path);
	}

	public TextMapIterable(InputStream inputStream) {
		super(inputStream);
	}

	public TextMapIterable(Reader reader) {
		super(reader);
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(File file) {
		WorkbookFileType fileType = WorkbookFileType.parse(file);
		return fileType.getObjectReader().readValues(file);
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(Path path) {
		WorkbookFileType fileType = WorkbookFileType.parse(path);
		return fileType.getObjectReader().readValues(path);
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(InputStream inputStream) {
		throw new UnsupportedOperationException("Iterator<Map<String, Object>> iterator(InputStream)");
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(Reader reader) {
		throw new UnsupportedOperationException("Iterator<Map<String, Object>> iterator(Reader)");
	}
}
