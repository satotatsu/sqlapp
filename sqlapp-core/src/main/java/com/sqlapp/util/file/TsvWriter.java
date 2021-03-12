/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.io.File;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import com.univocity.parsers.tsv.TsvWriterSettings;

public class TsvWriter extends AbstractFileWriter<com.univocity.parsers.tsv.TsvWriter>{

	public TsvWriter(final Writer writer, final Consumer<TsvWriterSettings> settingConsumer) {
		super(()->{
			final TsvWriterSettings settings = new TsvWriterSettings();
			settingConsumer.accept(settings);
			return new com.univocity.parsers.tsv.TsvWriter(writer, settings);
		});
	}

	public TsvWriter(final File file, final Charset charset, final Consumer<TsvWriterSettings> settingConsumer) {
		super(()->{
			final TsvWriterSettings settings = new TsvWriterSettings();
			settingConsumer.accept(settings);
			return new com.univocity.parsers.tsv.TsvWriter(file, charset , settings);
		});
	}

	public TsvWriter(final File file, final String charset, final Consumer<TsvWriterSettings> settingConsumer) {
		super(()->{
			final TsvWriterSettings settings = new TsvWriterSettings();
			settingConsumer.accept(settings);
			return new com.univocity.parsers.tsv.TsvWriter(file, charset , settings);
		});
	}

	public TsvWriter(final OutputStream os, final Charset charset, final Consumer<TsvWriterSettings> settingConsumer) {
		super(()->{
			final TsvWriterSettings settings = new TsvWriterSettings();
			settingConsumer.accept(settings);
			return new com.univocity.parsers.tsv.TsvWriter(os, charset , settings);
		});
	}

	public TsvWriter(final OutputStream os, final String charset, final Consumer<TsvWriterSettings> settingConsumer) {
		super(()->{
			final TsvWriterSettings settings = new TsvWriterSettings();
			settingConsumer.accept(settings);
			return new com.univocity.parsers.tsv.TsvWriter(os, charset , settings);
		});
	}

}
