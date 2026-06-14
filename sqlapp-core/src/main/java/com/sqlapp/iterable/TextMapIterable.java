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
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

import tools.jackson.databind.ObjectReader;

public class TextMapIterable extends AbstractTextMapIterable {

	private final ObjectReader objectReader;

	public TextMapIterable(File file, ObjectReader objectReader) {
		super(file);
		this.objectReader = objectReader;
	}

	public TextMapIterable(Path path, ObjectReader objectReader) {
		super(path);
		this.objectReader = objectReader;
	}

	public TextMapIterable(InputStream inputStream, ObjectReader objectReader) {
		super(inputStream);
		this.objectReader = objectReader;
	}

	public TextMapIterable(Reader reader, ObjectReader objectReader) {
		super(reader);
		this.objectReader = objectReader;
	}

	public ObjectReader getObjectReader() {
		return objectReader;
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(File file) {
		return getObjectReader().readValues(file);
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(Path path) {
		return getObjectReader().readValues(path);
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(InputStream inputStream) {
		return getObjectReader().readValues(inputStream);
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(Reader reader) {
		return getObjectReader().readValues(reader);
	}
}
