/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.normalization;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.migration.LegacyMigrationMappingValidator;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.RepeatColumn;
import com.sqlapp.data.schemas.RepeatColumnClusterBuilder;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;

import lombok.Getter;
import lombok.Setter;

/**
 * Generates a reviewable normalization proposal and a non-destructive preview
 * schema.
 */
@Getter
@Setter
public class GenerateNormalizationPlanCommand extends AbstractCommand {

	private static final Pattern DATE_NAME = Pattern.compile("(?i).*(?:DATE|_YMD|YYYYMMDD)$");

	private File targetFile;

	private File migrationMappingFile;

	private File outputDirectory = new File("./");

	private int minimumColumnCount = 2;

	private Predicate<Column> columnFilter = c -> true;

	private long variableCharacterMinimumLength = 20;

	private boolean previewSchemaEnabled = true;

	private Locale locale = Locale.getDefault();

	public static ResourceBundle getResourceBundle(Locale locale) {
		return ResourceBundle.getBundle(GenerateNormalizationPlanCommand.class.getPackageName() + ".messages", locale);
	}

	public static String getMessage(Locale locale, String key, Object... arguments) {
		String pattern = getResourceBundle(locale).getString(key);
		return new MessageFormat(pattern, locale).format(arguments);
	}

	private String message(String key, Object... arguments) {
		return getMessage(locale == null ? Locale.getDefault() : locale, key, arguments);
	}

	@Override
	protected void doRun() {
		validateProperties();
		try {
			var root = SchemaUtils.readXml(targetFile);
			String baseName = baseName(targetFile.getName());
			File planFile = new File(outputDirectory, baseName + "-normalization-plan.yaml");
			File previewFile = new File(outputDirectory, baseName + "-normalization-preview.xml");
			List<Map<String, Object>> candidates = candidates(SchemaUtils.toTables(root));
			if (previewSchemaEnabled) {
				writePreview(previewFile);
			}
			Map<String, Object> plan = map();
			plan.put("format", "sqlapp-normalization-plan");
			plan.put("version", 1);
			plan.put("source",
					map("schemaFile", targetFile.getAbsolutePath(), "schemaFingerprint", fingerprint(targetFile),
							"migrationMappingFile",
							migrationMappingFile == null ? null : migrationMappingFile.getAbsolutePath()));
			plan.put("preview",
					map("schemaFile", previewSchemaEnabled ? previewFile.getAbsolutePath() : null, "schemaFingerprint",
							previewSchemaEnabled ? fingerprint(previewFile) : null, "appliedCandidateCategories",
							List.of("repeating-columns", "composite-primary-key")));
			plan.put("configuration", map("minimumColumnCount", minimumColumnCount, "variableCharacterMinimumLength",
					variableCharacterMinimumLength));
			plan.put("candidates", candidates);
			writeYaml(planFile, plan);
			info(message("log.outputPlan", planFile.getAbsolutePath()));
			if (previewSchemaEnabled) {
				info(message("log.outputPreview", previewFile.getAbsolutePath()));
			}
		} catch (Exception e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException(message("error.generate", targetFile), e);
		}
	}

	private List<Map<String, Object>> candidates(List<Table> tables) {
		List<Map<String, Object>> result = new ArrayList<>();
		for (Table table : tables) {
			var clusters = RepeatColumnClusterBuilder.of(table).minimumColumnCount(minimumColumnCount)
					.columnFilter(columnFilter).build();
			int clusterNumber = 0;
			for (var cluster : clusters) {
				clusterNumber++;
				List<Map<String, Object>> columns = new ArrayList<>();
				for (RepeatColumn repeat : cluster) {
					columns.add(map("target", repeat.getBaseName(), "sourceColumns",
							repeat.getColumns().entrySet().stream()
									.map(entry -> map("index", entry.getKey(), "column", entry.getValue().getName()))
									.toList()));
				}
				result.add(candidate(table, "repeating-columns", "high",
						map("operation", "split-table", "targetTable", table.getName() + "_DETAIL_" + clusterNumber,
								"sequenceColumn", "ROW_NO", "indexes", cluster.getIndexes(), "columns", columns),
						List.of(message("question.repeatingColumns.sequenceOrder"),
								message("question.repeatingColumns.unusedAndNull"))));
			}
			if (table.getPrimaryKeyConstraint() == null) {
				result.add(candidate(table, "missing-primary-key", "high", map("operation", "review-required"),
						List.of(message("question.missingPrimaryKey.businessKey"))));
			} else if (table.getPrimaryKeyConstraint().getColumns().size() >= 2) {
				result.add(candidate(table, "composite-primary-key", "high",
						map("operation", "surrogate-key", "column", "ID", "dataType", "INT", "generation", "IDENTITY",
								"businessKey", table.getPrimaryKeyConstraint().getColumns().toColumns().stream()
										.map(Column::getName).toList()),
						List.of(message("question.compositePrimaryKey.keepUnique"))));
			}
			for (Column column : table.getColumns()) {
				if ((column.getDataType() == DataType.CHAR || column.getDataType() == DataType.VARCHAR)
						&& Long.valueOf(8L).equals(column.getLength())
						&& DATE_NAME.matcher(column.getName()).matches()) {
					result.add(candidate(table, column, "date-like-character", "medium",
							map("operation", "change-type", "from", column.getDataType() + "(8)", "to", "DATE",
									"format", "yyyyMMdd"),
							List.of(message("question.dateLikeCharacter.zeroAndBlank"),
									message("question.dateLikeCharacter.invalidDate"))));
				}
				if (column.getDataType() == DataType.CHAR && column.getLength() != null
						&& column.getLength() >= variableCharacterMinimumLength) {
					result.add(candidate(
							table, column, "char-to-varchar", "medium", map("operation", "change-type", "from", "CHAR",
									"to", "VARCHAR", "length", column.getLength()),
							List.of(message("question.character.trailingSpaces"))));
				} else if (column.getDataType() == DataType.NCHAR && column.getLength() != null
						&& column.getLength() >= variableCharacterMinimumLength) {
					result.add(candidate(
							table, column, "nchar-to-nvarchar", "medium", map("operation", "change-type", "from",
									"NCHAR", "to", "NVARCHAR", "length", column.getLength()),
							List.of(message("question.character.trailingSpaces"))));
				}
			}
		}
		return result;
	}

	private Map<String, Object> candidate(Table table, String category, String confidence, Map<String, Object> proposal,
			List<String> questions) {
		return candidate(table, null, category, confidence, proposal, questions);
	}

	private Map<String, Object> candidate(Table table, Column column, String category, String confidence,
			Map<String, Object> proposal, List<String> questions) {
		String suffix = column == null ? "" : "-" + column.getName();
		return map("id",
				("normalize-" + table.getName() + suffix + "-" + category).toLowerCase(Locale.ROOT).replace('_', '-'),
				"status", "proposed", "confidence", confidence, "category", category, "source",
				map("schema", table.getSchemaName(), "table", table.getName(), "column",
						column == null ? null : column.getName(), "remarks",
						column == null ? table.getRemarks() : column.getRemarks()),
				"proposal", proposal, "review", map("required", true, "questions", questions));
	}

	private void writePreview(File previewFile) throws IOException {
		File work = new File(outputDirectory, ".normalization-preview");
		FirstNormalFormCommand command = new FirstNormalFormCommand();
		command.setTargetFile(targetFile);
		command.setOutputDirectory(work);
		command.setColumnFilter(columnFilter);
		command.setMinimumColumnCount(minimumColumnCount);
		command.setConvertCompositePrimaryKey(true);
		command.setMigrationMappingEnabled(false);
		command.run();
		File generated = new File(work, targetFile.getName());
		Files.move(generated.toPath(), previewFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		Files.deleteIfExists(work.toPath());
	}

	private void writeYaml(File file, Map<String, Object> value) {
		File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
		new YamlConverter().writeJsonValue(temporary, value);
		try {
			Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new CommandException(message("error.writePlan", file), e);
		} finally {
			if (temporary.exists()) {
				temporary.delete();
			}
		}
	}

	private String fingerprint(File file) {
		return new LegacyMigrationMappingValidator().fingerprint(file);
	}

	private String baseName(String name) {
		int index = name.lastIndexOf('.');
		return index < 0 ? name : name.substring(0, index);
	}

	private void validateProperties() {
		if (targetFile == null || !targetFile.isFile()) {
			throw new CommandException(message("error.targetFile", targetFile));
		}
		if (migrationMappingFile != null && !migrationMappingFile.isFile()) {
			throw new CommandException(message("error.migrationMappingFile", migrationMappingFile));
		}
		if (outputDirectory == null) {
			throw new CommandException(message("error.outputDirectoryRequired"));
		}
		if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
			throw new CommandException(message("error.createOutputDirectory", outputDirectory));
		}
		if (minimumColumnCount < 1 || variableCharacterMinimumLength < 1) {
			throw new CommandException(message("error.candidateThresholds"));
		}
	}

	private Map<String, Object> map(Object... values) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (int i = 0; i < values.length; i += 2) {
			result.put((String) values[i], values[i + 1]);
		}
		return result;
	}
}
