package com.sqlapp.data.db.dialect.oracle.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.sqlapp.data.schemas.Event;

/** Strict parser for the qualified inline native job export subset. */
final class OracleSchedulerJobScript {
	private record Token(String text, boolean literal) {
	}

	record Call(String operation, String sql, List<String> bindings) {
		Call {
			bindings = List.copyOf(bindings);
		}
	}

	private record Value(String sql, List<String> bindings) {
	}

	private final List<Token> tokens = new ArrayList<>();
	private int position;

	private OracleSchedulerJobScript(String script) {
		for (int i = 0; i < script.length();) {
			char c = script.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
				continue;
			}
			if (c == '\'') {
				StringBuilder value = new StringBuilder();
				boolean closed = false;
				for (i++; i < script.length();) {
					char next = script.charAt(i++);
					if (next != '\'')
						value.append(next);
					else if (i < script.length() && script.charAt(i) == '\'') {
						value.append('\'');
						i++;
					} else {
						closed = true;
						break;
					}
				}
				if (!closed)
					throw invalid();
				tokens.add(new Token(value.toString(), true));
			} else if (Character.isLetterOrDigit(c) || c == '_') {
				int start = i++;
				while (i < script.length() && (Character.isLetterOrDigit(script.charAt(i)) || script.charAt(i) == '_'))
					i++;
				tokens.add(new Token(script.substring(start, i).toUpperCase(Locale.ROOT), false));
			} else if ("().,;=/".indexOf(c) >= 0) {
				if (c == '=' && i + 1 < script.length() && script.charAt(i + 1) == '>') {
					tokens.add(new Token("=>", false));
					i += 2;
				} else {
					tokens.add(new Token(Character.toString(c), false));
					i++;
				}
			} else
				throw invalid();
		}
	}

	static List<Call> parse(Event event) {
		if (!OracleSchedulerDefinition.fingerprint(event)
				.equals(event.getSpecifics().get(OracleSchedulerDefinition.FINGERPRINT)))
			throw new IllegalArgumentException(
					"Scheduler metadata changed or has no fingerprint; refresh a full read before restoration");
		var review = OracleSchedulerScriptReview.collect(List.of(event));
		if (review.scripts().size() != 1 || !"JOB".equals(field(event, "kind")))
			throw new IllegalArgumentException("Only inline Scheduler jobs can be restored");
		int count = argumentCount(event);
		if (count != number(field(event, "argumentCount"))
				|| count > 0 && !"STORED_PROCEDURE".equals(field(event, "JOB_TYPE")))
			throw new IllegalArgumentException(
					"Scheduler arguments must be complete inline stored-procedure arguments");
		for (String key : event.getSpecifics().keySet()) {
			String normalized = key.toLowerCase(Locale.ROOT);
			if (!normalized.startsWith("oracle.scheduler.argument."))
				continue;
			String[] parts = normalized.substring("oracle.scheduler.argument.".length()).split("\\.", -1);
			if (parts.length != 2 || number(parts[0]) < 1 || number(parts[0]) > count || !Set
					.of("name", "type", "value", "outargument", "hasanydatavalue", "representation").contains(parts[1]))
				throw invalid();
		}
		for (int position = 1; position <= count; position++) {
			String prefix = "argument." + position + ".";
			String type = field(event, prefix + "type"), out = field(event, prefix + "outArgument");
			if (!"TEXT".equals(field(event, prefix + "representation")) || field(event, prefix + "value") == null
					|| field(event, prefix + "value").isEmpty()
					|| !Set.of("TRUE", "FALSE").contains(required(field(event, prefix + "hasAnydataValue")))
					|| type != null && !Set.of("UNDEFINED", "VARCHAR2", "SYS.VARCHAR2").contains(type)
					|| out != null && !"FALSE".equals(out) || field(event, prefix + "name") != null)
				throw new IllegalArgumentException("Unsupported or unverifiable Scheduler argument: " + position
						+ "; representation=" + field(event, prefix + "representation") + ", type=" + type + ", OUT="
						+ out + ", name=" + field(event, prefix + "name"));
		}
		if (!Set.of("PLSQL_BLOCK", "STORED_PROCEDURE").contains(required(field(event, "JOB_TYPE")))
				|| field(event, "DESTINATION") != null || !"FALSE".equals(field(event, "RESTARTABLE"))
				|| !"3".equals(field(event, "JOB_PRIORITY")) || field(event, "MAX_RUNS") != null
				|| field(event, "MAX_FAILURES") != null)
			throw new IllegalArgumentException("Unsupported Scheduler job type, destination or execution attributes");
		var parser = new OracleSchedulerJobScript(review.scripts().getFirst().definition());
		return parser.parseJob(event);
	}

	private List<Call> parseJob(Event event) {
		expect("BEGIN");
		procedure("CREATE_JOB");
		expect("(");
		Map<String, Value> arguments = new LinkedHashMap<>();
		if (peek().literal())
			arguments.put("JOB_NAME", value());
		else {
			expect("JOB_NAME");
			expect("=>");
			arguments.put("JOB_NAME", value());
		}
		while (accept(",")) {
			Token name = next();
			if (name.literal()
					|| !Set.of("JOB_TYPE", "JOB_ACTION", "NUMBER_OF_ARGUMENTS", "START_DATE", "REPEAT_INTERVAL",
							"END_DATE", "JOB_CLASS", "ENABLED", "AUTO_DROP", "COMMENTS").contains(name.text()))
				throw invalid();
			expect("=>");
			if (arguments.putIfAbsent(name.text(), value()) != null)
				throw invalid();
		}
		expect(")");
		expect(";");
		if (arguments.size() != 11)
			throw invalid();
		identity(arguments.get("JOB_NAME"), event);
		if (!"FALSE".equals(arguments.get("ENABLED").sql())
				|| !Integer.toString(argumentCount(event)).equals(arguments.get("NUMBER_OF_ARGUMENTS").sql())
				|| !text(arguments.get("JOB_TYPE")).equals(field(event, "JOB_TYPE"))
				|| !text(arguments.get("JOB_ACTION")).replace("\r\n", "\n")
						.equals(String.join("\n", event.getStatement()))
				|| !arguments.get("AUTO_DROP").sql().equals(field(event, "AUTO_DROP")))
			throw invalid();
		for (String name : List.of("JOB_NAME", "JOB_TYPE", "JOB_ACTION", "JOB_CLASS"))
			text(arguments.get(name));
		for (String name : List.of("REPEAT_INTERVAL", "COMMENTS"))
			nullableText(arguments.get(name));
		for (String name : List.of("START_DATE", "END_DATE")) {
			String expression = arguments.get(name).sql();
			if (!expression.equals("NULL") && !expression.startsWith("TO_TIMESTAMP_TZ("))
				throw invalid();
		}
		if (!Set.of("TRUE", "FALSE").contains(arguments.get("AUTO_DROP").sql()))
			throw invalid();
		List<String> bindings = new ArrayList<>();
		StringBuilder sql = new StringBuilder("BEGIN SYS.DBMS_SCHEDULER.CREATE_JOB(");
		for (var argument : arguments.entrySet()) {
			if (!bindings.isEmpty())
				sql.append(",");
			sql.append(argument.getKey()).append("=>").append(argument.getValue().sql());
			bindings.addAll(argument.getValue().bindings());
		}
		sql.append("); END;");
		List<Call> calls = new ArrayList<>();
		calls.add(new Call("CREATE_JOB", sql.toString(), bindings));
		procedure("SET_ATTRIBUTE");
		expect("(");
		Value job = value();
		identity(job, event);
		expect(",");
		Value attribute = value();
		if (!"NLS_ENV".equals(text(attribute)))
			throw invalid();
		expect(",");
		Value nls = value();
		if (!text(nls).equals(field(event, "NLS_ENV")))
			throw invalid();
		expect(")");
		expect(";");
		calls.add(new Call("SET_NLS_ENV", "BEGIN SYS.DBMS_SCHEDULER.SET_ATTRIBUTE(?,?,?); END;",
				List.of(text(job), text(attribute), text(nls))));
		expect("COMMIT");
		expect(";");
		expect("END");
		expect(";");
		accept("/");
		if (position != tokens.size())
			throw invalid();
		for (int index = 1; index <= argumentCount(event); index++)
			calls.add(new Call("SET_ARGUMENT_" + index,
					"BEGIN SYS.DBMS_SCHEDULER.SET_JOB_ARGUMENT_VALUE(job_name=>?,argument_position=>" + index
							+ ",argument_value=>?); END;",
					List.of(text(job), field(event, "argument." + index + ".value"))));
		return List.copyOf(calls);
	}

	private Value value() {
		Token token = next();
		if (token.literal())
			return new Value("?", List.of(token.text()));
		if (Set.of("NULL", "TRUE", "FALSE").contains(token.text()))
			return new Value(token.text(), List.of());
		if (token.text().matches("[0-9]+"))
			return new Value(Integer.toString(number(token.text())), List.of());
		if (!token.text().equals("TO_TIMESTAMP_TZ"))
			throw invalid();
		expect("(");
		List<String> bindings = new ArrayList<>();
		do {
			Token literal = next();
			if (!literal.literal())
				throw invalid();
			bindings.add(literal.text());
		} while (accept(","));
		expect(")");
		if (bindings.size() < 2 || bindings.size() > 3)
			throw invalid();
		return new Value(
				"TO_TIMESTAMP_TZ(" + String.join(",", java.util.Collections.nCopies(bindings.size(), "?")) + ")",
				bindings);
	}

	private void procedure(String name) {
		if (accept("SYS"))
			expect(".");
		expect("DBMS_SCHEDULER");
		expect(".");
		expect(name);
	}

	private static void identity(Value value, Event event) {
		String name = "\"" + event.getName().replace("\"", "\"\"") + "\"";
		String qualified = "\"" + event.getSchemaName().replace("\"", "\"\"") + "\"." + name;
		if (!text(value).equals(name) && !text(value).equals(qualified))
			throw invalid();
	}

	private static String nullableText(Value value) {
		return value.sql().equals("NULL") ? null : text(value);
	}

	private static String text(Value value) {
		if (value == null || !value.sql().equals("?") || value.bindings().size() != 1)
			throw invalid();
		return value.bindings().getFirst();
	}

	private static String field(Event event, String key) {
		return event.getSpecifics().get("oracle.scheduler." + key);
	}

	private static String required(String value) {
		if (value == null)
			throw invalid();
		return value;
	}

	static int argumentCount(Event event) {
		return number(field(event, "NUMBER_OF_ARGUMENTS"));
	}

	private static int number(String value) {
		if (value == null || !value.matches("0|[1-9][0-9]{0,2}"))
			throw invalid();
		int result = Integer.parseInt(value);
		if (result > 255)
			throw invalid();
		return result;
	}

	private Token peek() {
		if (position >= tokens.size())
			throw invalid();
		return tokens.get(position);
	}

	private Token next() {
		Token result = peek();
		position++;
		return result;
	}

	private boolean accept(String text) {
		if (position < tokens.size() && !peek().literal() && peek().text().equals(text)) {
			position++;
			return true;
		}
		return false;
	}

	private void expect(String text) {
		if (!accept(text))
			throw invalid();
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException(
				"Unsupported or stale native Scheduler job script; explicit migration required");
	}
}
