/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.export;

import java.io.IOException;
import java.io.File;
import java.util.Map;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.db.command.properties.PlaceholderProperty;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.data.schemas.rowiterator.ExpressionConverter;
import com.sqlapp.exceptions.InvalidValueException;

/** Shared expression evaluation and input diagnostics for file readers. */
final class FileRowValueConverter {
	private FileRowValueConverter() {
	}

	static ExpressionConverter createExpressions(final File directory, final PlaceholderProperty settings) {
		final ExpressionConverter converter = new ExpressionConverter();
		converter.setFileDirectory(directory);
		converter.setPlaceholderPrefix(settings.getPlaceholderPrefix());
		converter.setPlaceholderSuffix(settings.getPlaceholderSuffix());
		converter.setPlaceholders(settings.isPlaceholders());
		return converter;
	}

	static RowValueConverter create(final ExpressionConverter expressions, final Map<String, Object> variables,
			final RowValueConverter customConverter) {
		final ParametersContext context = new ParametersContext();
		context.putAll(variables);
		return (row, column, value) -> {
			final Object converted = customConverter == null ? value : customConverter.apply(row, column, value);
			try {
				return expressions.convert(converted, context);
			} catch (final IOException e) {
				throw new InvalidValueException(row, column, value, e);
			}
		};
	}
}
