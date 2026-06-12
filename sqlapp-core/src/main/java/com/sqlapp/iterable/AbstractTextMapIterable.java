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

import com.sqlapp.util.iterator.AutoCloseIterator;

public abstract class AbstractTextMapIterable implements MapIterable {
	private final File file;
	private final Path path;
	private final InputStream inputStream;
	private final Reader reader;

	public AbstractTextMapIterable(File file) {
		this.file = file;
		this.path = null;
		this.inputStream = null;
		this.reader = null;
	}

	public AbstractTextMapIterable(Path path) {
		this.file = null;
		this.path = path;
		this.inputStream = null;
		this.reader = null;
	}

	public AbstractTextMapIterable(InputStream inputStream) {
		this.file = null;
		this.path = null;
		this.inputStream = inputStream;
		this.reader = null;
	}

	public AbstractTextMapIterable(Reader reader) {
		this.file = null;
		this.path = null;
		this.inputStream = null;
		this.reader = reader;
	}

	@Override
	public Iterator<Map<String, Object>> iterator() {
		if (file != null) {
			return new AutoCloseIterator<Map<String, Object>>(iterator(file));
		}
		if (path != null) {
			return new AutoCloseIterator<Map<String, Object>>(iterator(path));
		}
		if (inputStream != null) {
			return new AutoCloseIterator<Map<String, Object>>(iterator(inputStream));
		}
		return new AutoCloseIterator<Map<String, Object>>(iterator(reader));
	}

	protected abstract Iterator<Map<String, Object>> iterator(File file);

	protected abstract Iterator<Map<String, Object>> iterator(Path path);

	protected abstract Iterator<Map<String, Object>> iterator(InputStream inputStream);

	protected abstract Iterator<Map<String, Object>> iterator(Reader reader);

}
