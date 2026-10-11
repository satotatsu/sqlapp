package com.sqlapp.data.db.command.generator.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.db.command.generator.config.FileGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;
import com.sqlapp.util.eval.mvel.MvelCompiledEvaluator;
import com.sqlapp.util.eval.mvel.SqlappParserContextFactory;

class GeneratorMvelContextTest {
	@Test
	void keepsInputNamesLocalToEachCompiledExpression() {
		final var parser = SqlappParserContextFactory.getInstance().getParserContext();
		parser.addImport(File.class);
		final var previous = new MvelCompiledEvaluator("getName", parser);
		final var context = new ParametersContext();
		context.put("getName", "previous-input");
		assertEquals("previous-input", previous.eval(context));
		final var next = new MvelCompiledEvaluator("new File('aaa.png').getName()", parser);
		assertEquals("aaa.png", next.eval(new ParametersContext()));
	}

	@Test
	void evaluatesAFileDataSourceOnlyOncePerLoad() {
		final var calls = new AtomicInteger();
		final var table = new TableGeneratorConfig();
		table.setEvaluator(new CachedMvelEvaluator() {
			@Override
			@SuppressWarnings("unchecked")
			public <T> T eval(final String expression, final Map<?, ?> context) {
				return (T) List.of(Map.of("VALUE", calls.incrementAndGet()));
			}
		});
		final var file = new FileGeneratorConfig();
		file.setTableGeneratorConfig(table);
		file.setDataSourceExpression("source()");
		file.loadData();
		assertEquals(1, calls.get());
		assertEquals(1, file.getValues().get(0).get("VALUE"));
	}

	@Test
	void resolvesGeneratorFileImportsAndFullyQualifiedConstructors() {
		final var evaluator = CachedMvelEvaluatorUtils.getCachedMvelEvaluator();
		final var context = new ParametersContext();
		context.put("path", "aaa.png");
		assertEquals(new File("aaa.png"), evaluator.eval("new File(path)", context));
		assertEquals(new File("aaa.png"), evaluator.eval("new java.io.File(path)", (Object) context));
		assertEquals(new File("aaa.png"), evaluator.eval("new File(path)", (Map<?, ?>) context));
		assertTrue(evaluator.evalBoolean("new File(path).getName() == 'aaa.png'", context));
	}

	@Test
	void preservesNullSqlParametersAssignmentsAndExplicitVariables() {
		final var evaluator = CachedMvelEvaluatorUtils.getCachedMvelEvaluator();
		final var context = new ParametersContext();
		assertTrue(evaluator.evalBoolean("missing == null", context));
		assertNull(evaluator.eval("missing", context));
		evaluator.eval("assigned = 7", context);
		assertEquals(7, context.get("assigned"));
		context.put("explicit", "explicit-value");
		assertEquals("explicit-value", evaluator.eval("explicit", context));
	}

	@Test
	void typedCompiledEvaluationUsesTheSameResolver() {
		final var parser = SqlappParserContextFactory.getInstance().getParserContext();
		parser.addImport(File.class);
		final var evaluator = new MvelCompiledEvaluator("new File(path)", parser);
		final var context = new ParametersContext();
		context.put("path", "aaa.png");
		assertEquals(new File("aaa.png"), evaluator.eval(context, File.class));
	}
}
