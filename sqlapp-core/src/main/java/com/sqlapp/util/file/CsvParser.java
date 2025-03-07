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
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import com.univocity.parsers.csv.Csv;
import com.univocity.parsers.csv.CsvParserSettings;

public class CsvParser extends AbstractFileParser<com.univocity.parsers.csv.CsvParser, CsvParserSettings>{

	public CsvParser(final Reader reader, final Consumer<CsvParserSettings> settingConsumer) {
		super(createCsvParserSettings(), settingConsumer, (settings)->{
			return new com.univocity.parsers.csv.CsvParser(settings);
		}, reader);
	}

	public CsvParser(final File file, final Charset charset, final Consumer<CsvParserSettings> settingsConsumer) {
		super(createCsvParserSettings(), settingsConsumer, (settings)->{
			return new com.univocity.parsers.csv.CsvParser(settings);
		}, file, charset);
	}
	
	public CsvParser(final InputStream is, final Charset charset, final Consumer<CsvParserSettings> settingsConsumer) {
		super(createCsvParserSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.csv.CsvParser(settings);
		}, is, charset);
	}

	public CsvParser(final File file, final String charset, final Consumer<CsvParserSettings> settingsConsumer) {
		super(createCsvParserSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.csv.CsvParser(settings);
		}, file, charset);
	}

	public CsvParser(final InputStream is, final String charset, final Consumer<CsvParserSettings> settingsConsumer) {
		super(createCsvParserSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.csv.CsvParser(settings);
		}, is, charset);
	}

	private static CsvParserSettings createCsvParserSettings(){
		final CsvParserSettings settings = Csv.parseExcel();
		settings.setEmptyValue("");
		return settings;
	}
	
}
