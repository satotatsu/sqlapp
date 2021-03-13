/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import com.univocity.parsers.tsv.TsvParserSettings;

public class TsvParser extends AbstractFileParser<com.univocity.parsers.tsv.TsvParser, TsvParserSettings>{

	public TsvParser(final Reader reader, final Consumer<TsvParserSettings> settingsConsumer) {
		super(new TsvParserSettings(), settingsConsumer, (settings)->{
			return new com.univocity.parsers.tsv.TsvParser(settings);
		}, reader);
	}

	public TsvParser(final File file, final Charset charset, final Consumer<TsvParserSettings> settingsConsumer) {
		super(new TsvParserSettings(), settingsConsumer, (settings)->{
			return new com.univocity.parsers.tsv.TsvParser(settings);
		}, file, charset);
	}
	
	public TsvParser(final InputStream is, final Charset charset, final Consumer<TsvParserSettings> settingsConsumer) {
		super(new TsvParserSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.tsv.TsvParser(settings);
		}, is, charset);
	}

	public TsvParser(final File file, final String charset, final Consumer<TsvParserSettings> settingsConsumer) {
		super(new TsvParserSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.tsv.TsvParser(settings);
		}, file, charset);
	}

	public TsvParser(final InputStream is, final String charset, final Consumer<TsvParserSettings> settingsConsumer) {
		super(new TsvParserSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.tsv.TsvParser(settings);
		}, is, charset);
	}
}
