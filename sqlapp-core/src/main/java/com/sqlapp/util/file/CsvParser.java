/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.sqlapp.data.schemas.Table;
import com.univocity.parsers.csv.CsvParserSettings;

public class CsvParser extends AbstractFileParser<com.univocity.parsers.csv.CsvParser>{

	public CsvParser(final Consumer<CsvParserSettings> settingConsumer) {
		super(()->{
			final CsvParserSettings settings = new CsvParserSettings();
			settingConsumer.accept(settings);
			return new com.univocity.parsers.csv.CsvParser(settings);
		});
	}

	public CsvParser(final Table table, final Consumer<CsvParserSettings> settingConsumer) {
		super(()->{
			final CsvParserSettings settings = new CsvParserSettings();
			settings.setHeaders(table.getColumns().stream().map(c->c.getName()).collect(Collectors.toList()).toArray(new String[0]));
			settingConsumer.accept(settings);
			return new com.univocity.parsers.csv.CsvParser(settings);
		});
	}

}
