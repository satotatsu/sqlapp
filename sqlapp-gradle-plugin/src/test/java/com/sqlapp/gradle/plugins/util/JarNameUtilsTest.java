package com.sqlapp.gradle.plugins.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.junit.jupiter.api.Test;

class JarNameUtilsTest {

	@Test
	void test() {
		assertEquals(true, JarNameUtils.isTarget(new File("sqlapp-core-0.40.0.jar")));
		assertEquals(true, JarNameUtils.isTarget(new File("sqlapp-command-0.40.0.jar")));
		assertEquals(true, JarNameUtils.isTarget(new File("sqlapp-core.jar")));
		assertEquals(true, JarNameUtils.isTarget(new File("sqlapp-command.jar")));
		assertEquals(false, JarNameUtils.isTarget(new File("sqlapp-core-hsql-0.40.0.jar")));
	}

}
