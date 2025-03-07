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
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import com.univocity.parsers.common.AbstractParser;
import com.univocity.parsers.common.CommonParserSettings;
import com.univocity.parsers.common.Format;

public class TextFileReader implements AutoCloseable{
	
	private final AbstractFileParser<? extends AbstractParser<?>, ? extends CommonParserSettings<?>> fileParser;
	
	public TextFileReader(final AbstractFileParser<? extends AbstractParser<?>, ? extends CommonParserSettings<?>> fileParser) {
		this.fileParser=fileParser;
	}

	public <X extends CommonParserSettings<? extends Format>> TextFileReader(final FileType fileType, final Reader reader, final Consumer<X> setting) {
		this.fileParser=fileType.createParser(reader, setting);
	}

	public <X extends CommonParserSettings<? extends Format>> TextFileReader(final FileType fileType, final File file, final Charset charset, final Consumer<X> setting) {
		this.fileParser=fileType.createParser(file, charset, setting);
	}
	
	public <X extends CommonParserSettings<? extends Format>> TextFileReader(final FileType fileType, final InputStream is, final Charset charset, final Consumer<X> setting) {
		this.fileParser=fileType.createParser(is, charset, setting);
	}

	public <X extends CommonParserSettings<? extends Format>> TextFileReader(final FileType fileType, final File file, final String charset, final Consumer<X> setting) {
		this.fileParser=fileType.createParser(file, charset, setting);
	}

	public <X extends CommonParserSettings<? extends Format>> TextFileReader(final FileType fileType, final InputStream is, final String charset, final Consumer<X> setting) {
		this.fileParser=fileType.createParser(is, charset, setting);
	}

	/**
	 * Gets the current position in the file, where the first line of the file is line number 1.
	 * 
	 * @return the line number
	 */
	public long getLineNumber() {
		return this.fileParser.getLineNumber();
	}
	
	public String[] read() throws IOException{
		this.fileParser.beginParsing();
		return fileParser.parseNext();
	}

	@Override
	public void close() throws Exception {
		this.fileParser.close();
	}
}
