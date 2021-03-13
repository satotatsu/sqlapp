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
		return parser.parseNext();
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
