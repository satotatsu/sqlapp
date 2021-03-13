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
/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.sqlapp.util.FileUtils;
import com.univocity.parsers.common.AbstractParser;
import com.univocity.parsers.common.ArgumentUtils;
import com.univocity.parsers.common.CommonParserSettings;
import com.univocity.parsers.common.record.Record;

public abstract class AbstractFileParser<T extends AbstractParser<?>, S extends CommonParserSettings<?>> implements AutoCloseable{
	
	private final T parser;

	private final Reader reader;
	
	private boolean startParsing=false;
	
	private long lineNumber=0;
	
	AbstractFileParser(final S settings, final Consumer<S> settingConsumer, final Function<S,T> parserFunction, final Reader reader){
		settingConsumer.accept(settings);
		this.parser=parserFunction.apply(settings);
		this.reader=wrap(reader);
	}

	AbstractFileParser(final S settings, final Consumer<S> settingConsumer, final Function<S,T> parserFunction, final File file, final Charset charset){
		settingConsumer.accept(settings);
		this.parser=parserFunction.apply(settings);
		this.reader=wrap(ArgumentUtils.newReader(file, charset));
	}

	AbstractFileParser(final S settings, final Consumer<S> settingConsumer, final Function<S,T> parserFunction, final File file, final String charset){
		settingConsumer.accept(settings);
		this.parser=parserFunction.apply(settings);
		this.reader=wrap(ArgumentUtils.newReader(file, charset));
	}

	AbstractFileParser(final S settings, final Consumer<S> settingConsumer, final Function<S,T> parserFunction, final InputStream is, final Charset charset){
		settingConsumer.accept(settings);
		this.parser=parserFunction.apply(settings);
		this.reader=wrap(ArgumentUtils.newReader(is, charset));
	}

	AbstractFileParser(final S settings, final Consumer<S> settingConsumer, final Function<S,T> parserFunction, final InputStream is, final String charset){
		settingConsumer.accept(settings);
		this.parser=parserFunction.apply(settings);
		this.reader=wrap(ArgumentUtils.newReader(is, charset));
	}

	private Reader wrap(final Reader reader) {
		if (reader instanceof BufferedReader) {
			return reader;
		}
		return new BufferedReader(reader);
	}
	
	public void beginParsing() {
		if (!startParsing) {
	        parser.beginParsing(reader);
		}
        startParsing=true;
	}
	
	public String[] parseNext() {
		lineNumber++;
		return parser.parseNext();
	}

	public Record parseNextRecord() {
		lineNumber++;
		return parser.parseNextRecord();
	}

	public long getLineNumber() {
		return lineNumber;
	}

	public void readAll(final BiConsumer<String[], Long> cons) throws IOException {
		beginParsing();
        long i=0;
        String[] row;
        while ((row = parser.parseNext()) != null) {
        	cons.accept(row, i++);
        }
	}

	public void readAllRecord(final InputStream is, final Charset charset, final BiConsumer<Record, Long> cons) throws IOException {
		beginParsing();
        long i=0;
        for(final Record record : parser.iterateRecords(reader)){
        	cons.accept(record, i++);
        }
	}
	
	protected void initialize(final S settings) {
		settings.setMaxCharsPerColumn(8192);
	}

	@Override
	public void close() {
		FileUtils.close(reader);
		parser.stopParsing();
	}
	
}
