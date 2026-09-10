/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/**
 * Generates an AI-ready specification and PL/I template for CSV extraction.
 */
@Getter
@Setter
public class GeneratePliCsvExtractorCommand extends AbstractCommand {

	private File contractFile;

	private File outputDirectory = new File("./");

	private String programName = "SQLAPEXT";

	@Override
	protected void doRun() {
		if (contractFile == null || !contractFile.isFile()) {
			throw new CommandException("Migration contract file does not exist: " + contractFile);
		}
		if (outputDirectory == null) {
			throw new CommandException("Output directory is required.");
		}
		programName = programName == null ? null : programName.toUpperCase(Locale.ROOT);
		if (programName == null || !programName.matches("[A-Z][A-Z0-9@#$]{0,7}")) {
			throw new CommandException("PL/I program name must be 1-8 characters: " + programName);
		}
		var contract = new LegacyMigrationContractIO().read(contractFile);
		var generator = new PliCsvExtractorGenerator();
		generator.validate(contract);
		write(new File(outputDirectory, programName + "-extraction-spec.md"),
				generator.specification(contract, programName));
		write(new File(outputDirectory, programName + ".pli.template"),
				generator.template(contract, programName));
		info("PL/I CSV extraction artifacts: ", outputDirectory.getAbsolutePath());
	}

	private void write(File file, String value) {
		try {
			AtomicMigrationFile.write(file.toPath(), temporary ->
					Files.writeString(temporary, value, StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to write PL/I extraction artifact: " + file, e);
		}
	}
}
