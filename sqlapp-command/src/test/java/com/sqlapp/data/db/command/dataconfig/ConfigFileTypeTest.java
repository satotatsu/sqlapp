/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.dataconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;

import org.junit.jupiter.api.Test;

class ConfigFileTypeTest {

	@Test
	void parsesFilesWithTheSharedDataFormatRules() {
		assertEquals(ConfigFileType.YAML, ConfigFileType.parse(new File("CONFIG.YML")));
		assertEquals(ConfigFileType.JSON, ConfigFileType.parse(new File("config.JSON")));
		assertEquals(ConfigFileType.TOML, ConfigFileType.parse(new File("config/directory/config.toml")));
		assertNull(ConfigFileType.parse(new File("config.unknown")));
		assertNull(ConfigFileType.parse((File) null));
	}
}
