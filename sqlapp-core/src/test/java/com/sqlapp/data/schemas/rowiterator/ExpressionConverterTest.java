package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.util.eval.mvel.MvelUtils;

class ExpressionConverterTest {
	@TempDir
	Path directory;

	@Test
	void readsFileConstructorsWithLiteralAndContextPaths() throws Exception {
		final byte[] bytes = { 0, 1, (byte) 255 };
		final Path file = Files.write(directory.resolve("aaa.png"), bytes);
		final var context = new ParametersContext();
		context.put("path", "aaa.png");
		final var converter = converter();
		assertArrayEquals(bytes, (byte[]) converter.convert("${new File('aaa.png')}", context));
		assertArrayEquals(bytes, (byte[]) converter.convert("${new File(path)}", context));
		context.put("path", file.toString());
		assertArrayEquals(bytes, (byte[]) converter.convert("${new java.io.File(path)}", context));
	}

	@Test
	void supportsNonFileExpressionsWithoutDirectoryAndDisabledPlaceholders() throws Exception {
		final var converter = new ExpressionConverter();
		final var context = new ParametersContext();
		context.put("number", 3);
		assertEquals("${number + 1}", converter.convert("${number + 1}", context));
		converter.setPlaceholders(true);
		assertEquals(4, converter.convert("${number + 1}", context));
	}

	@Test
	void restoresPreviousMvelBasePathAfterEvaluation() throws Exception {
		MvelUtils.setBasePath("previous");
		try {
			final var converter = converter();
			assertEquals(2, converter.convert("${1 + 1}", new ParametersContext()));
			assertEquals("previous", MvelUtils.getBasePath());
		} finally {
			MvelUtils.setBasePath(null);
		}
	}

	@Test
	void missingFileIsAnIoFailure() {
		assertThrows(IOException.class, () -> converter().convert("${new File('absent.png')}", new ParametersContext()));
	}

	private ExpressionConverter converter() {
		final var converter = new ExpressionConverter();
		converter.setPlaceholders(true);
		converter.setFileDirectory(directory.toFile());
		return converter;
	}
}
