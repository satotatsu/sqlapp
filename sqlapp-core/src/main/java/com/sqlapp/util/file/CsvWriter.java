/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.io.File;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import com.univocity.parsers.csv.CsvWriterSettings;

public class CsvWriter extends AbstractFileWriter<com.univocity.parsers.csv.CsvWriter>{

	public CsvWriter(final Writer writer, final Consumer<CsvWriterSettings> settingConsumer) {
		super(()->{
			final CsvWriterSettings settings = new CsvWriterSettings();
			settingConsumer.accept(settings);
			return new com.univocity.parsers.csv.CsvWriter(writer, settings);
		});
	}

	public CsvWriter(final File file, final Charset charset, final Consumer<CsvWriterSettings> settingConsumer) {
		super(()->{
			final CsvWriterSettings settings = new CsvWriterSettings();
			settingConsumer.accept(settings);
			return new com.univocity.parsers.csv.CsvWriter(file, charset, settings);
		});
	}
	
	public CsvWriter(final OutputStream os, final Charset charset, final Consumer<CsvWriterSettings> settingConsumer) {
		super(()->{
			final CsvWriterSettings settings = new CsvWriterSettings();
			settingConsumer.accept(settings);
			return new com.univocity.parsers.csv.CsvWriter(os, charset, settings);
		});
	}

	public CsvWriter(final File file, final String charset, final Consumer<CsvWriterSettings> settingConsumer) {
		super(()->{
			final CsvWriterSettings settings = new CsvWriterSettings();
			settingConsumer.accept(settings);
			return new com.univocity.parsers.csv.CsvWriter(file, charset, settings);
		});
	}

	public CsvWriter(final OutputStream os, final String charset, final Consumer<CsvWriterSettings> settingConsumer) {
		super(()->{
			final CsvWriterSettings settings = new CsvWriterSettings();
			settingConsumer.accept(settings);
			return new com.univocity.parsers.csv.CsvWriter(os, charset, settings);
		});
	}

}
