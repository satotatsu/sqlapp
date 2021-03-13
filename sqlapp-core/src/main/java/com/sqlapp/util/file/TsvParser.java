/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.sqlapp.data.schemas.Table;
import com.univocity.parsers.tsv.TsvParserSettings;

public class TsvParser extends AbstractFileParser<com.univocity.parsers.tsv.TsvParser, TsvParserSettings>{

	public TsvParser(final Consumer<TsvParserSettings> settingConsumer) {
		super(new TsvParserSettings(), settingConsumer, (settings)->{
			return new com.univocity.parsers.tsv.TsvParser(settings);
		});
	}

	public TsvParser(final Table table, final Consumer<TsvParserSettings> settingConsumer) {
		super(new TsvParserSettings(), s->{
			s.setHeaders(table.getColumns().stream().map(c->c.getName()).collect(Collectors.toList()).toArray(new String[0]));
		}, (settings)->{
			return new com.univocity.parsers.tsv.TsvParser(settings);
		});
	}
}
