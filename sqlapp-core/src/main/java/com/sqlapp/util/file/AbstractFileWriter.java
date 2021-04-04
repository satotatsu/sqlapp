/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.util.file;

import java.util.function.Consumer;
import java.util.function.Function;

import com.univocity.parsers.common.AbstractWriter;
import com.univocity.parsers.common.CommonWriterSettings;

public abstract class AbstractFileWriter<T extends AbstractWriter<?>, S extends CommonWriterSettings<?>> implements AutoCloseable{

	private final T writer;

	public AbstractFileWriter(final S settings, final Consumer<S> settingsConsumer, final Function<S,T> writerFunction) {
		initialize(settings);
		settingsConsumer.accept(settings);
		this.writer=writerFunction.apply(settings);
	}
	
	@Override
	public void close() {
		writer.close();
	}

	public void writeHeader(final String... arg) {
		this.writer.writeHeaders(arg);
	}

	public void writeRow(final String... arg)  {
		this.writer.writeRow(arg);
	}

	public void writeEmptyRow()  {
		this.writer.writeEmptyRow();
	}

	protected void initialize(final S settings) {
		settings.setMaxCharsPerColumn(8192);
	}
}
