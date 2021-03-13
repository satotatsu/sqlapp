/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import com.univocity.parsers.fixed.FixedWidthParserSettings;

public class FixedWidthParser extends AbstractFileParser<com.univocity.parsers.fixed.FixedWidthParser, FixedWidthParserSettings>{

	public FixedWidthParser(final Reader reader, final Consumer<FixedWidthParserSettings> settingConsumer) {
		super(new FixedWidthParserSettings(), settingConsumer, (settings)->{
			return new com.univocity.parsers.fixed.FixedWidthParser(settings);
		}, reader);
	}

	public FixedWidthParser(final File file, final Charset charset, final Consumer<FixedWidthParserSettings> settingsConsumer) {
		super(new FixedWidthParserSettings(), settingsConsumer, (settings)->{
			return new com.univocity.parsers.fixed.FixedWidthParser(settings);
		}, file, charset);
	}
	
	public FixedWidthParser(final InputStream is, final Charset charset, final Consumer<FixedWidthParserSettings> settingsConsumer) {
		super(new FixedWidthParserSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.fixed.FixedWidthParser(settings);
		}, is, charset);
	}

	public FixedWidthParser(final File file, final String charset, final Consumer<FixedWidthParserSettings> settingsConsumer) {
		super(new FixedWidthParserSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.fixed.FixedWidthParser(settings);
		}, file, charset);
	}

	public FixedWidthParser(final InputStream is, final String charset, final Consumer<FixedWidthParserSettings> settingsConsumer) {
		super(new FixedWidthParserSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.fixed.FixedWidthParser(settings);
		}, is, charset);
	}
}
