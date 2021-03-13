/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import com.univocity.parsers.csv.CsvParserSettings;

public class CsvParser extends AbstractFileParser<com.univocity.parsers.csv.CsvParser, CsvParserSettings>{

	public CsvParser(final Reader reader, final Consumer<CsvParserSettings> settingConsumer) {
		super(new CsvParserSettings(), settingConsumer, (settings)->{
			return new com.univocity.parsers.csv.CsvParser(settings);
		}, reader);
	}

	public CsvParser(final File file, final Charset charset, final Consumer<CsvParserSettings> settingsConsumer) {
		super(new CsvParserSettings(), settingsConsumer, (settings)->{
			return new com.univocity.parsers.csv.CsvParser(settings);
		}, file, charset);
	}
	
	public CsvParser(final InputStream is, final Charset charset, final Consumer<CsvParserSettings> settingsConsumer) {
		super(new CsvParserSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.csv.CsvParser(settings);
		}, is, charset);
	}

	public CsvParser(final File file, final String charset, final Consumer<CsvParserSettings> settingsConsumer) {
		super(new CsvParserSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.csv.CsvParser(settings);
		}, file, charset);
	}

	public CsvParser(final InputStream is, final String charset, final Consumer<CsvParserSettings> settingsConsumer) {
		super(new CsvParserSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.csv.CsvParser(settings);
		}, is, charset);
	}

}
