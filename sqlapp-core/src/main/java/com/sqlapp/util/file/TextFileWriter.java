/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.util.file;

import java.io.File;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import com.univocity.parsers.common.AbstractWriter;
import com.univocity.parsers.common.CommonWriterSettings;
import com.univocity.parsers.common.Format;

public class TextFileWriter implements AutoCloseable{

	private final AbstractFileWriter<? extends AbstractWriter<?>, ? extends CommonWriterSettings<?>> fileWriter;
	
	public TextFileWriter(final AbstractFileWriter<? extends AbstractWriter<?>, ? extends CommonWriterSettings<?>> fileWriter) {
		this.fileWriter=fileWriter;
	}
	
	
	public <X extends CommonWriterSettings<? extends Format>> TextFileWriter(final FileType fileType, final Writer writer, final Consumer<X> setting) {
		this.fileWriter=fileType.createWriter(writer, setting);
	}

	public <X extends CommonWriterSettings<? extends Format>> TextFileWriter(final FileType fileType, final File file, final Charset charset, final Consumer<X> setting) {
		this.fileWriter=fileType.createWriter(file, charset, setting);
	}
	
	public <X extends CommonWriterSettings<? extends Format>> TextFileWriter(final FileType fileType, final OutputStream os, final Charset charset, final Consumer<X> setting) {
		this.fileWriter=fileType.createWriter(os, charset, setting);
	}

	public <X extends CommonWriterSettings<? extends Format>> TextFileWriter(final FileType fileType, final File file, final String charset, final Consumer<X> setting) {
		this.fileWriter=fileType.createWriter(file, charset, setting);
	}

	public <X extends CommonWriterSettings<? extends Format>> TextFileWriter(final FileType fileType, final OutputStream os, final String charset, final Consumer<X> setting) {
		this.fileWriter=fileType.createWriter(os, charset, setting);
	}

	
	@Override
	public void close() throws Exception {
		fileWriter.close();
	}

	public void writeHeader(final String... arg) {
		this.fileWriter.writeHeader(arg);
	}

	public void writeRow(final String... arg)  {
		this.fileWriter.writeRow(arg);
	}

	public void writeEmptyRow()  {
		this.fileWriter.writeEmptyRow();
	}
}
