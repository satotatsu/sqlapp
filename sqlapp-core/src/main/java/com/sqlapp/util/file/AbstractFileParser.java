/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.univocity.parsers.common.AbstractParser;
import com.univocity.parsers.common.CommonParserSettings;
import com.univocity.parsers.common.record.Record;

public abstract class AbstractFileParser<T extends AbstractParser<?>, S extends CommonParserSettings<?>> implements AutoCloseable{
	
	private final T parser;
	
	AbstractFileParser(final S settings, final Consumer<S> settingConsumer, final Function<S,T> parserFunction){
		settingConsumer.accept(settings);
		this.parser=parserFunction.apply(settings);
	}
	
	public void read(final File file, final Charset charset, final BiConsumer<String[], Long> cons) throws IOException {
        read(new FileInputStream(file), charset, cons);
	}

	public void read(final File file, final String charset, final BiConsumer<String[], Long> cons) throws IOException {
        read(new FileInputStream(file), Charset.forName(charset), cons);
	}

	public void read(final InputStream is, final String charset, final BiConsumer<String[], Long> cons) throws IOException {
        read(is, Charset.forName(charset), cons);
	}

	public void readRecord(final File file, final Charset charset, final BiConsumer<Record, Long> cons) throws IOException {
		readRecord(new FileInputStream(file), charset, cons);
	}

	public void readRecord(final File file, final String charset, final BiConsumer<Record, Long> cons) throws IOException {
		readRecord(new FileInputStream(file), Charset.forName(charset), cons);
	}

	public void readRecord(final InputStream is, final String charset, final BiConsumer<Record, Long> cons) throws IOException {
		readRecord(is, Charset.forName(charset), cons);
	}
	
	public void read(final InputStream is, final Charset charset, final BiConsumer<String[], Long> cons) throws IOException {
        try (final BufferedReader br= new BufferedReader(new InputStreamReader(is, charset))) {
            parser.beginParsing(br);
            long i=0;
            String[] row;
            while ((row = parser.parseNext()) != null) {
            	cons.accept(row, i++);
            }
        } finally {
        	close();
        }
	}

	public void read(final Reader reader, final BiConsumer<String[], Long> cons) throws IOException {
        try (final BufferedReader br= new BufferedReader(reader)) {
            parser.beginParsing(br);
            long i=0;
            String[] row;
            while ((row = parser.parseNext()) != null) {
            	cons.accept(row, i++);
            }
        } finally {
        	close();
        }
	}

	public void readRecord(final InputStream is, final Charset charset, final BiConsumer<Record, Long> cons) throws IOException {
        try (final BufferedReader br= new BufferedReader(new InputStreamReader(is, charset))) {
            parser.beginParsing(br);
            long i=0;
            for(final Record record : parser.iterateRecords(br)){
            	cons.accept(record, i++);
            }
        } finally {
        	close();
        }
	}

	public void readRecord(final Reader reader, final BiConsumer<Record, Long> cons) throws IOException {
        try (final BufferedReader br= new BufferedReader(reader)) {
            parser.beginParsing(br);
            long i=0;
            for(final Record record : parser.iterateRecords(br)){
            	cons.accept(record, i++);
            }
        } finally {
        	close();
        }
	}
	
	protected void initialize(final S settings) {
		settings.setMaxCharsPerColumn(8192);
	}
	
	
	
	@Override
	public void close() {
		parser.stopParsing();
	}
	
}
