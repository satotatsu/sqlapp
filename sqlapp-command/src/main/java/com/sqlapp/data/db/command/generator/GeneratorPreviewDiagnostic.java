package com.sqlapp.data.db.command.generator;

/**
 * An advisory schema mismatch in a preview sample, before JDBC or database
 * conversion.
 */
public record GeneratorPreviewDiagnostic(GeneratorPreviewRow row, String columnName, Code code, String message) {
	public enum Code {
		NULL_FOR_NOT_NULL, CHARACTER_LENGTH_EXCEEDED, NUMERIC_INTEGER_DIGITS_EXCEEDED, NUMERIC_SCALE_EXCEEDED
	}
}
