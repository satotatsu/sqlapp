package com.sqlapp.data.db.dialect.oracle.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.data.db.dialect.oracle.util.OracleSchedulerScriptReview.Kind;
import com.sqlapp.data.db.dialect.oracle.util.OracleSchedulerScriptReview.Script;

/** Strict parser for the supported native job export shapes. */
final class OracleSchedulerJobScript {
	record Plan(List<Call> calls, List<Script> dependencies, Map<Integer, String> arguments) {
		Plan {
			calls = List.copyOf(calls);
			dependencies = List.copyOf(dependencies);
			arguments = java.util.Collections.unmodifiableMap(new TreeMap<>(arguments));
		}
	}

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

	static Plan parse(Event event) {
		if (!OracleSchedulerDefinition.fingerprint(event)
				.equals(event.getSpecifics().get(OracleSchedulerDefinition.FINGERPRINT)))
			throw new IllegalArgumentException(
					"Scheduler metadata changed or has no fingerprint; refresh a full read before restoration");
		var review = OracleSchedulerScriptReview.collect(List.of(event));
		var dependencies = review.scripts().stream().filter(script -> script.kind() != Kind.JOB).toList();
		boolean namedProgram = dependencies.stream().anyMatch(script -> script.kind() == Kind.PROGRAM);
		if (!"JOB".equals(field(event, "kind"))
				|| dependencies.stream().anyMatch(script -> !event.getSchemaName().equals(script.owner())))
			throw new IllegalArgumentException(
					"Scheduler restoration requires same-owner Program and Schedule references");
		String type = field(event, namedProgram ? "program.PROGRAM_TYPE" : "JOB_TYPE");
		int count = number(field(event, namedProgram ? "program.NUMBER_OF_ARGUMENTS" : "NUMBER_OF_ARGUMENTS"));
		if (!Set.of("PLSQL_BLOCK", "STORED_PROCEDURE").contains(required(type))
				|| count > 0 && !"STORED_PROCEDURE".equals(type) || field(event, "DESTINATION") != null
				|| !"FALSE".equals(field(event, "RESTARTABLE")) || !"3".equals(field(event, "JOB_PRIORITY"))
				|| field(event, "MAX_RUNS") != null || field(event, "MAX_FAILURES") != null)
			throw new IllegalArgumentException("Unsupported Scheduler job type, destination or execution attributes");
		if (namedProgram) {
			if (count != number(field(event, "program.argumentCount")))
				throw invalid();
			for (int index = 1; index <= count; index++) {
				String prefix = "program.argument." + index + ".";
				String anydata = field(event, prefix + "hasDefaultAnydataValue");
				boolean verifiedText = "TRUE".equals(anydata)
						&& "TEXT".equals(field(event, prefix + "defaultRepresentation"))
						&& field(event, prefix + "defaultValue") != null;
				if (!"FALSE".equals(anydata) && !verifiedText)
					throw new IllegalArgumentException(
							"Scheduler Program has an opaque or unverified default at position " + index);
			}
		}
		Map<Integer, String> overrides = new TreeMap<>();
		for (String key : event.getSpecifics().keySet()) {
			String normalized = key.toLowerCase(Locale.ROOT);
			if (!normalized.startsWith("oracle.scheduler.argument."))
				continue;
			String[] parts = normalized.substring("oracle.scheduler.argument.".length()).split("\\.", -1);
			if (parts.length != 2 || number(parts[0]) < 1 || number(parts[0]) > count || !Set
					.of("name", "type", "value", "outargument", "hasanydatavalue", "representation").contains(parts[1]))
				throw invalid();
			overrides.put(number(parts[0]), null);
		}
		if (overrides.size() != number(field(event, "argumentCount")) || !namedProgram && overrides.size() != count)
			throw new IllegalArgumentException("Incomplete Scheduler argument inventory");
		for (var entry : overrides.entrySet()) {
			int position = entry.getKey();
			String prefix = "argument." + position + ".";
			String argumentType = field(event, prefix + "type"), out = field(event, prefix + "outArgument");
			String name = field(event, prefix + "name");
			if (!"TEXT".equals(field(event, prefix + "representation")) || field(event, prefix + "value") == null
					|| field(event, prefix + "value").isEmpty()
					|| !Set.of("TRUE", "FALSE").contains(required(field(event, prefix + "hasAnydataValue")))
					|| argumentType != null && !Set.of("UNDEFINED", "VARCHAR2", "SYS.VARCHAR2").contains(argumentType)
					|| out != null && !"FALSE".equals(out)
					|| name != null && (!namedProgram || !name.equals(field(event, "program." + prefix + "name"))))
				throw new IllegalArgumentException("Unsupported or unverifiable Scheduler argument: " + position
						+ "; representation=" + field(event, prefix + "representation") + ", type=" + argumentType
						+ ", OUT=" + out + ", name=" + name);
			entry.setValue(field(event, prefix + "value"));
		}
		var parser = new OracleSchedulerJobScript(review.scripts().getLast().definition());
		return new Plan(parser.parseJob(event, dependencies, overrides, count, namedProgram), dependencies, overrides);
	}

	static List<Call> createDependency(Event event, Script dependency) {
		var parser = new OracleSchedulerJobScript(dependency.definition());
		return parser.parseDependency(event, dependency);
	}

	private List<Call> parseDependency(Event event, Script dependency) {
		boolean program = dependency.kind() == Kind.PROGRAM;
		String operation = program ? "CREATE_PROGRAM" : "CREATE_SCHEDULE";
		expect("BEGIN");
		// Oracle exports this validation bypass before CREATE_SCHEDULE. Recognize its
		// shape only.
		if (!program && acceptProcedure("DISABLE1_CALENDAR_CHECK")) {
			expect("(");
			expect(")");
			expect(";");
		}
		procedure(operation);
		expect("(");
		List<Value> values = new ArrayList<>();
		do {
			values.add(value());
		} while (accept(","));
		expect(")");
		expect(";");
		expectEnd();
		if (values.size() != (program ? 6 : 5))
			throw invalid();
		identity(values.getFirst(), dependency.owner(), dependency.name());
		List<Call> calls = new ArrayList<>();
		if (program) {
			int count = number(field(event, "program.NUMBER_OF_ARGUMENTS"));
			if (!text(values.get(1)).equals(field(event, "program.PROGRAM_TYPE"))
					|| !text(values.get(2)).equals(field(event, "program.PROGRAM_ACTION"))
					|| !Integer.toString(count).equals(values.get(3).sql()) || !"FALSE".equals(values.get(4).sql())
					|| !java.util.Objects.equals(nullableText(values.get(5)), field(event, "program.COMMENTS")))
				throw invalid();
			calls.add(dependencyCall(operation, values));
			for (int index = 1; index <= count; index++) {
				String prefix = "program.argument." + index + ".";
				String defaultValue = field(event, prefix + "defaultValue");
				String out = field(event, prefix + "outArgument");
				if (field(event, prefix + "metadataAttribute") != null || out != null && !"FALSE".equals(out)
						|| (defaultValue != null) != "TEXT".equals(field(event, prefix + "defaultRepresentation"))
						|| defaultValue == null && !"FALSE".equals(field(event, prefix + "hasDefaultAnydataValue")))
					throw new IllegalArgumentException(
							"Cannot recreate Scheduler Program argument at position " + index);
				List<Value> argument = new ArrayList<>(
						List.of(values.getFirst(), new Value(Integer.toString(index), List.of()),
								bound(field(event, prefix + "name")), bound(required(field(event, prefix + "type")))));
				if (defaultValue != null)
					argument.add(bound(defaultValue));
				argument.add(new Value("FALSE", List.of()));
				calls.add(dependencyCall("DEFINE_PROGRAM_ARGUMENT", argument, "DEFINE_PROGRAM_ARGUMENT_" + index));
			}
		} else {
			checkDate(values.get(1));
			checkDate(values.get(3));
			String interval = nullableText(values.get(2));
			if (!java.util.Objects.equals(interval, field(event, "schedule.REPEAT_INTERVAL"))
					|| !java.util.Objects.equals(nullableText(values.get(4)), field(event, "schedule.COMMENTS")))
				throw invalid();
			checkIndependentCalendar(interval);
			calls.add(dependencyCall(operation, values));
		}
		return List.copyOf(calls);
	}

	private static void checkIndependentCalendar(String interval) {
		if (interval == null)
			return;
		String calendar = interval.strip().toUpperCase(Locale.ROOT);
		if (!calendar.matches("(?s)FREQ\\s*=\\s*(YEARLY|MONTHLY|WEEKLY|DAILY|HOURLY|MINUTELY|SECONDLY)(\\s*;.*)?")
				|| java.util.regex.Pattern.compile("(?:^|;)\\s*(INCLUDE|EXCLUDE|INTERSECT)\\s*=").matcher(calendar)
						.find())
			throw new IllegalArgumentException("Creating a Scheduler Schedule requires an independent calendar");
	}

	private static Value bound(String text) {
		return text == null ? new Value("NULL", List.of()) : new Value("?", List.of(text));
	}

	private static Call dependencyCall(String procedure, List<Value> values) {
		return dependencyCall(procedure, values, procedure);
	}

	private static Call dependencyCall(String procedure, List<Value> values, String operation) {
		return new Call(operation,
				"BEGIN SYS.DBMS_SCHEDULER." + procedure + "("
						+ String.join(",", values.stream().map(Value::sql).toList()) + "); END;",
				values.stream().flatMap(value -> value.bindings().stream()).toList());
	}

	private List<Call> parseJob(Event event, List<Script> dependencies, Map<Integer, String> overrides, int count,
			boolean namedProgram) {
		boolean namedSchedule = dependencies.stream().anyMatch(script -> script.kind() == Kind.SCHEDULE);
		Set<String> expected = new java.util.HashSet<>(
				Set.of("JOB_NAME", "JOB_CLASS", "ENABLED", "AUTO_DROP", "COMMENTS"));
		expected.addAll(
				namedProgram ? Set.of("PROGRAM_NAME") : Set.of("JOB_TYPE", "JOB_ACTION", "NUMBER_OF_ARGUMENTS"));
		expected.addAll(namedSchedule ? Set.of("SCHEDULE_NAME") : Set.of("START_DATE", "REPEAT_INTERVAL", "END_DATE"));
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
			if (name.literal() || !expected.contains(name.text()))
				throw invalid();
			expect("=>");
			if (arguments.putIfAbsent(name.text(), value()) != null)
				throw invalid();
		}
		expect(")");
		expect(";");
		if (!arguments.keySet().equals(expected))
			throw invalid();
		identity(arguments.get("JOB_NAME"), event);
		if (!"FALSE".equals(arguments.get("ENABLED").sql())
				|| !arguments.get("AUTO_DROP").sql().equals(field(event, "AUTO_DROP")))
			throw invalid();
		text(arguments.get("JOB_CLASS"));
		nullableText(arguments.get("COMMENTS"));
		for (Script dependency : dependencies)
			identity(arguments.get(dependency.kind().name() + "_NAME"), dependency.owner(), dependency.name());
		if (!namedProgram) {
			if (!Integer.toString(count).equals(arguments.get("NUMBER_OF_ARGUMENTS").sql())
					|| !text(arguments.get("JOB_TYPE")).equals(field(event, "JOB_TYPE"))
					|| !text(arguments.get("JOB_ACTION")).replace("\r\n", "\n")
							.equals(String.join("\n", event.getStatement())))
				throw invalid();
		}
		if (!namedSchedule) {
			nullableText(arguments.get("REPEAT_INTERVAL"));
			checkDate(arguments.get("START_DATE"));
			checkDate(arguments.get("END_DATE"));
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
		expectEnd();
		for (var entry : overrides.entrySet())
			calls.add(
					new Call("SET_ARGUMENT_" + entry.getKey(),
							"BEGIN SYS.DBMS_SCHEDULER.SET_JOB_ARGUMENT_VALUE(job_name=>?,argument_position=>"
									+ entry.getKey() + ",argument_value=>?); END;",
							List.of(text(job), entry.getValue())));
		return List.copyOf(calls);
	}

	private static void checkDate(Value value) {
		if (!value.sql().equals("NULL") && !value.sql().startsWith("TO_TIMESTAMP_TZ("))
			throw invalid();
	}

	private void expectEnd() {
		expect("COMMIT");
		expect(";");
		expect("END");
		expect(";");
		accept("/");
		if (position != tokens.size())
			throw invalid();
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
		if (!acceptProcedure(name))
			throw invalid();
	}

	private boolean acceptProcedure(String name) {
		int start = position;
		if ((!accept("SYS") || accept(".")) && accept("DBMS_SCHEDULER") && accept(".") && accept(name))
			return true;
		position = start;
		return false;
	}

	private static void identity(Value value, Event event) {
		identity(value, event.getSchemaName(), event.getName());
	}

	private static void identity(Value value, String owner, String objectName) {
		String name = "\"" + objectName.replace("\"", "\"\"") + "\"";
		String qualified = "\"" + owner.replace("\"", "\"\"") + "\"." + name;
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
