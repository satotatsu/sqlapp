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

import com.univocity.parsers.csv.CsvWriterSettings;

public class CsvWriter extends AbstractFileWriter<com.univocity.parsers.csv.CsvWriter, CsvWriterSettings>{

	public CsvWriter(final Writer writer, final Consumer<CsvWriterSettings> settingsConsumer) {
		super(new CsvWriterSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.csv.CsvWriter(writer, settings);
		});
	}

	public CsvWriter(final File file, final Charset charset, final Consumer<CsvWriterSettings> settingsConsumer) {
		super(new CsvWriterSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.csv.CsvWriter(file, charset, settings);
		});
	}
	
	public CsvWriter(final OutputStream os, final Charset charset, final Consumer<CsvWriterSettings> settingsConsumer) {
		super(new CsvWriterSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.csv.CsvWriter(os, charset, settings);
		});
	}

	public CsvWriter(final File file, final String charset, final Consumer<CsvWriterSettings> settingsConsumer) {
		super(new CsvWriterSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.csv.CsvWriter(file, charset, settings);
		});
	}

	public CsvWriter(final OutputStream os, final String charset, final Consumer<CsvWriterSettings> settingsConsumer) {
		super(new CsvWriterSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.csv.CsvWriter(os, charset, settings);
		});
	}

}
