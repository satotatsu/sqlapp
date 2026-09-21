/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.sqlapp.data.db.command.test.AbstractTest;
import com.sqlapp.data.schemas.rowiterator.DataFormat;

public class ConvertDataCommandTest extends AbstractTest {

	@Test
	public void testRun(@TempDir File directoryPath) throws ParseException, IOException, SQLException {
		File outputDir = new File(directoryPath, "output");
		outputDir.mkdirs();
		ConvertDataCommand command = new ConvertDataCommand();
		command.setDirectory(directoryPath);
		command.setOutputFileType(DataFormat.EXCEL);
		command.setOutputDirectory(outputDir);
		command.run();
	}

	@ParameterizedTest
	@MethodSource("additionalInputFormats")
	void convertsFormatsSupportedByImport(final String fileName, final String content,
			@TempDir final Path directory) throws Exception {
		final Path input = Files.writeString(directory.resolve(fileName), content);
		Files.writeString(directory.resolve("items.json"), "stale");
		final ConvertDataCommand command = new ConvertDataCommand();
		command.setFiles(List.of(input.toFile()));
		command.setOutputFileType(DataFormat.JSON);

		command.run();

		final String output = Files.readString(directory.resolve("items.json"));
		assertTrue(output.contains("\"ID\""));
		assertTrue(output.contains("1"));
		assertTrue(output.contains("2"));
	}

	@Test
	void rejectsTomlOutput(@TempDir final Path directory) throws Exception {
		final Path input = Files.writeString(directory.resolve("items.json"), "[{\"ID\":1}]");
		final ConvertDataCommand command = new ConvertDataCommand();
		command.setFiles(List.of(input.toFile()));
		command.setOutputFileType(DataFormat.TOML);

		final IllegalArgumentException error = assertThrows(IllegalArgumentException.class, command::run);
		assertEquals("TOML output is not supported because TOML has no root array.", error.getMessage());
	}

	@Test
	void removesShortNamedInputAfterSuccessfulConversion(@TempDir final Path directory) throws Exception {
		final Path input = Files.writeString(directory.resolve("a.jsonl"), "{\"ID\":1}");
		final ConvertDataCommand command = new ConvertDataCommand();
		command.setFiles(List.of(input.toFile()));
		command.setOutputFileType(DataFormat.JSON);
		command.setRemoveOriginalFile(true);

		command.run();

		assertFalse(Files.exists(input));
		assertTrue(Files.readString(directory.resolve("a.json")).contains("\"ID\""));
	}

	@Test
	void rejectsMissingExplicitInput(@TempDir final Path directory) {
		final Path input = directory.resolve("missing.json");
		final ConvertDataCommand command = new ConvertDataCommand();
		command.setFiles(List.of(input.toFile()));
		command.setOutputFileType(DataFormat.CSV);

		final IllegalArgumentException error = assertThrows(IllegalArgumentException.class, command::run);
		assertTrue(error.getMessage().contains("missing.json"));
	}

	@Test
	void rejectsUnsupportedExplicitInput(@TempDir final Path directory) throws Exception {
		final Path input = Files.writeString(directory.resolve("items.txt"), "data");
		final ConvertDataCommand command = new ConvertDataCommand();
		command.setFiles(List.of(input.toFile()));
		command.setOutputFileType(DataFormat.CSV);

		final IllegalArgumentException error = assertThrows(IllegalArgumentException.class, command::run);
		assertTrue(error.getMessage().contains("items.txt"));
	}

	private static Stream<Arguments> additionalInputFormats() {
		return Stream.of(
				Arguments.of("items.jsonl", "{\"ID\":1}\n{\"ID\":2}"),
				Arguments.of("items.yaml", "---\n- ID: 1\n- ID: 2\n"));
	}

}
