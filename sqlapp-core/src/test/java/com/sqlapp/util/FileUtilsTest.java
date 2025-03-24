package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUtilsTest {
	@TempDir
	private File tempDir;

	@Test
	void testGetFileNameWithoutExtension() {
		assertEquals("abc.def", FileUtils.getFileNameWithoutExtension("abc.def.xlsx"));
	}

}
