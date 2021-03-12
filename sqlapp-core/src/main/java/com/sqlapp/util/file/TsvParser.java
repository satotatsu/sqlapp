/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.sqlapp.data.schemas.Table;
import com.univocity.parsers.tsv.TsvParserSettings;

public class TsvParser extends AbstractFileParser<com.univocity.parsers.tsv.TsvParser>{

	public TsvParser(final Consumer<TsvParserSettings> settingConsumer) {
		super(()->{
			final TsvParserSettings settings = new TsvParserSettings();
			settingConsumer.accept(settings);
			return new com.univocity.parsers.tsv.TsvParser(settings);
		});
	}

	public TsvParser(final Table table, final Consumer<TsvParserSettings> settingConsumer) {
		super(()->{
			final TsvParserSettings settings = new TsvParserSettings();
			settings.setHeaders(table.getColumns().stream().map(c->c.getName()).collect(Collectors.toList()).toArray(new String[0]));
			settingConsumer.accept(settings);
			return new com.univocity.parsers.tsv.TsvParser(settings);
		});
	}
}
