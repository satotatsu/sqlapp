/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/**
 * Generates a CSV extraction, staging and hierarchical load contract.
 */
@Getter
@Setter
public class GenerateLegacyMigrationContractCommand extends AbstractCommand {

	private File mappingFile;

	private File outputDirectory = new File("./");

	private String outputFileName;

	private String encoding = "UTF-8";

	private String delimiter = ",";

	private String quote = "\"";

	private String nullValue = "";

	private boolean header = true;

	private String recordSeparator = "CRLF";

	@Override
	protected void doRun() {
		if (mappingFile == null || !mappingFile.isFile()) {
			throw new CommandException("Migration mapping file does not exist: " + mappingFile);
		}
		if (outputDirectory == null) {
			throw new CommandException("Output directory is required.");
		}
		if (delimiter == null || delimiter.isEmpty()) {
			throw new CommandException("CSV delimiter must not be empty.");
		}
		LegacyMigrationMappingIO mappingIO = new LegacyMigrationMappingIO();
		var contract = new LegacyMigrationContractBuilder().build(mappingFile, mappingIO.read(mappingFile));
		contract.getCsv().setEncoding(encoding);
		contract.getCsv().setDelimiter(delimiter);
		contract.getCsv().setQuote(quote);
		contract.getCsv().setNullValue(nullValue);
		contract.getCsv().setHeader(header);
		contract.getCsv().setRecordSeparator(recordSeparator);
		File outputFile = new File(outputDirectory,
				outputFileName == null || outputFileName.isBlank()
						? baseName(mappingFile.getName()) + "-contract.yaml" : outputFileName);
		new LegacyMigrationContractIO().write(outputFile, contract);
		info("Legacy migration contract: ", outputFile.getAbsolutePath());
	}

	private String baseName(String name) {
		String suffix = "-legacy-migration.yaml";
		if (name.endsWith(suffix)) {
			return name.substring(0, name.length() - suffix.length());
		}
		int index = name.lastIndexOf('.');
		return index < 0 ? name : name.substring(0, index);
	}
}
