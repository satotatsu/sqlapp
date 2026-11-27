/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.cobol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.command.host.cobol.CobolItem.Category;
import com.sqlapp.data.db.command.host.cobol.CobolItem.Usage;
import com.sqlapp.exceptions.CommandException;

/**
 * Parses the data description entries of a COBOL copybook and computes their
 * storage layout.
 * <p>
 * Supported: fixed (columns 7-72) and free source format, levels 01-49, 77 and
 * 88, PICTURE, USAGE (DISPLAY, COMP, COMP-4, BINARY, COMP-3, PACKED-DECIMAL,
 * COMP-5, COMP-1, COMP-2, NATIONAL, DISPLAY-1, INDEX), SIGN LEADING/TRAILING
 * [SEPARATE], OCCURS, OCCURS DEPENDING ON as the last item of a record,
 * REDEFINES, FILLER and VALUE. Entries that change the layout in a way that is
 * not computed (SYNCHRONIZED, POINTER, OCCURS DEPENDING ON followed by other
 * items) are rejected; level 66 RENAMES is skipped with a warning.
 * </p>
 */
public final class CobolCopybookParser {

	/** Source reference format. */
	public enum SourceFormat {
		/** Sequence area 1-6, indicator 7, program text 8-72. */
		FIXED,
		/** Free format; {@code *>} starts a comment. */
		FREE
	}

	private SourceFormat sourceFormat = SourceFormat.FIXED;

	private Map<String, String> replacing = new LinkedHashMap<>();

	private String defaultRecordName = "RECORD";

	public CobolCopybookParser setSourceFormat(SourceFormat sourceFormat) {
		this.sourceFormat = sourceFormat == null ? SourceFormat.FIXED : sourceFormat;
		return this;
	}

	/**
	 * Text replacements applied before parsing, like {@code COPY ... REPLACING
	 * ==:PREFIX:== BY ==CUST==}; keys and values are plain text.
	 */
	public CobolCopybookParser setReplacing(Map<String, String> replacing) {
		this.replacing = replacing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(replacing);
		return this;
	}

	/** Record name used when the copybook has no level-01 entry. */
	public CobolCopybookParser setDefaultRecordName(String defaultRecordName) {
		this.defaultRecordName = defaultRecordName;
		return this;
	}

	public CobolCopybook parse(String source) {
		List<String> warnings = new ArrayList<>();
		List<Entry> entries = new Splitter().split(replace(source));
		List<CobolItem> records = build(entries, warnings);
		if (records.isEmpty()) {
			throw new CommandException("No COBOL data description entries were found.");
		}
		for (CobolItem record : records) {
			resolve(record, record);
			layout(record, 0);
			validateDependingOn(record);
		}
		return new CobolCopybook(records, warnings);
	}

	private String replace(String source) {
		String result = source;
		for (Map.Entry<String, String> entry : replacing.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isEmpty()) {
				throw new CommandException("COBOL replacing text must not be empty.");
			}
			result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
		}
		return result;
	}

	// ------------------------------------------------------------------ entries

	private record Entry(String text, String remarks, int line) {
	}

	/** Splits source text into period-terminated entries with their comments. */
	private final class Splitter {
		private final List<Entry> entries = new ArrayList<>();
		private final StringBuilder code = new StringBuilder();
		private final List<String> pendingComments = new ArrayList<>();
		private final List<String> inlineComments = new ArrayList<>();
		private char quote;
		private int entryLine;
		private int lastFinishedOn = -1;

		List<Entry> split(String source) {
			String[] lines = source.replace("\t", "    ").split("\r?\n|\r", -1);
			for (int i = 0; i < lines.length; i++) {
				line(lines[i], i + 1);
			}
			if (!code.toString().isBlank()) {
				throw new CommandException(
						"COBOL entry is not terminated by a period: line " + entryLine + ": " + code.toString().trim());
			}
			return entries;
		}

		private void line(String line, int number) {
			String content;
			boolean continuation = false;
			if (sourceFormat == SourceFormat.FIXED) {
				if (line.length() < 7) {
					return;
				}
				char indicator = line.charAt(6);
				content = line.length() > 72 ? line.substring(7, 72) : line.substring(7);
				if (indicator == '*' || indicator == '/') {
					comment(content.trim());
					return;
				}
				if (indicator == 'D' || indicator == 'd') {
					return;
				}
				if (indicator == '-') {
					continuation = true;
				} else if (indicator != ' ') {
					throw new CommandException("Invalid indicator '" + indicator + "' at line " + number + ".");
				}
			} else {
				content = line;
				if (content.trim().startsWith("*>")) {
					comment(content.trim().substring(2).trim());
					return;
				}
			}
			String trimmed = content.trim().toUpperCase(Locale.ROOT);
			if (quote == 0 && !continuation && (trimmed.matches("(EJECT|SKIP[123])\\.?") || trimmed.startsWith(">>"))) {
				return;
			}
			if (continuation) {
				String rest = stripLeading(content);
				if (quote != 0) {
					if (rest.isEmpty() || rest.charAt(0) != quote) {
						throw new CommandException("Continued literal must start with a quote at line " + number + ".");
					}
					rest = rest.substring(1);
				} else {
					trimTrailing(code);
				}
				scan(rest, number);
			} else {
				if (quote != 0) {
					throw new CommandException("Unterminated literal before line " + number + ".");
				}
				scan(content, number);
			}
			if (quote == 0) {
				trimTrailing(code);
				code.append(' ');
			} else if (sourceFormat == SourceFormat.FIXED) {
				// A literal continued on the next line includes the spaces up to column 72.
				code.append(" ".repeat(Math.max(0, 65 - content.length())));
			}
		}

		private void comment(String text) {
			if (!text.isEmpty()) {
				pendingComments.add(text);
			}
		}

		private void scan(String content, int number) {
			for (int i = 0; i < content.length(); i++) {
				char c = content.charAt(i);
				if (quote != 0) {
					code.append(c);
					if (c == quote) {
						if (i + 1 < content.length() && content.charAt(i + 1) == quote) {
							code.append(content.charAt(++i));
						} else {
							quote = 0;
						}
					}
					continue;
				}
				if (c == '*' && i + 1 < content.length() && content.charAt(i + 1) == '>') {
					String text = content.substring(i + 2).trim();
					if (text.isEmpty()) {
						return;
					}
					Entry previous = entries.isEmpty() ? null : entries.getLast();
					if (code.toString().isBlank() && previous != null && lastFinishedOn == number) {
						// A comment after the period describes the entry that ended on this line.
						entries.set(entries.size() - 1, new Entry(previous.text(),
								previous.remarks() == null ? text : previous.remarks() + " " + text, previous.line()));
					} else {
						inlineComments.add(text);
					}
					return;
				}
				if (c == '\'' || c == '"') {
					quote = c;
				}
				if (code.toString().isBlank() && !Character.isWhitespace(c)) {
					entryLine = number;
				}
				if (c == '.' && (i + 1 == content.length() || Character.isWhitespace(content.charAt(i + 1)))) {
					finish();
					lastFinishedOn = number;
					continue;
				}
				code.append(c);
			}
		}

		private void finish() {
			String text = code.toString().trim();
			code.setLength(0);
			List<String> remarks = new ArrayList<>(pendingComments);
			remarks.addAll(inlineComments);
			pendingComments.clear();
			inlineComments.clear();
			if (!text.isEmpty()) {
				entries.add(new Entry(text, remarks.isEmpty() ? null : String.join(" ", remarks), entryLine));
			}
		}
	}

	private static String stripLeading(String value) {
		int i = 0;
		while (i < value.length() && Character.isWhitespace(value.charAt(i))) {
			i++;
		}
		return value.substring(i);
	}

	private static void trimTrailing(StringBuilder builder) {
		int length = builder.length();
		while (length > 0 && Character.isWhitespace(builder.charAt(length - 1))) {
			length--;
		}
		builder.setLength(length);
	}

	// ------------------------------------------------------------------ clauses

	private static final Set<String> CLAUSES = Set.of("PIC", "PICTURE", "USAGE", "REDEFINES", "OCCURS", "VALUE",
			"VALUES", "SIGN", "LEADING", "TRAILING", "JUST", "JUSTIFIED", "BLANK", "SYNC", "SYNCHRONIZED", "GLOBAL",
			"EXTERNAL", "RENAMES", "DISPLAY", "DISPLAY-1", "NATIONAL", "COMP", "COMPUTATIONAL", "COMP-1",
			"COMPUTATIONAL-1", "COMP-2", "COMPUTATIONAL-2", "COMP-3", "COMPUTATIONAL-3", "COMP-4", "COMPUTATIONAL-4",
			"COMP-5", "COMPUTATIONAL-5", "BINARY", "PACKED-DECIMAL", "INDEX", "POINTER", "FUNCTION-POINTER",
			"PROCEDURE-POINTER", "INDEXED", "ASCENDING", "DESCENDING", "SEPARATE", "IS", "ARE");

	private static final Pattern NUMBER = Pattern.compile("\\d+");

	private static final Pattern NAME = Pattern.compile("[A-Z0-9][A-Z0-9_-]*");

	private List<CobolItem> build(List<Entry> entries, List<String> warnings) {
		List<CobolItem> records = new ArrayList<>();
		Deque<CobolItem> stack = new ArrayDeque<>();
		CobolItem last = null;
		CobolItem synthetic = null;
		boolean skippingRenames = false;
		for (Entry entry : entries) {
			List<String> tokens = tokens(entry.text());
			if (tokens.isEmpty() || !NUMBER.matcher(tokens.get(0)).matches()) {
				throw new CommandException(
						"COBOL entry must start with a level number: line " + entry.line() + ": " + entry.text());
			}
			int level = Integer.parseInt(tokens.get(0));
			if (level == 66) {
				warnings.add("Level 66 RENAMES is not imported: line " + entry.line() + ": " + entry.text());
				skippingRenames = true;
				continue;
			}
			if (level == 88) {
				if (last == null || skippingRenames) {
					throw new CommandException("Level 88 has no conditional variable: line " + entry.line());
				}
				last.conditions.add(condition(tokens, entry));
				continue;
			}
			skippingRenames = false;
			if (level < 1 || level > 49 && level != 77) {
				throw new CommandException("Invalid COBOL level number " + level + ": line " + entry.line());
			}
			CobolItem item = item(level, tokens, entry);
			if (level == 1 || level == 77) {
				stack.clear();
				records.add(item);
				synthetic = null;
			} else {
				while (!stack.isEmpty() && stack.peek().level >= level) {
					stack.pop();
				}
				if (stack.isEmpty()) {
					if (synthetic == null) {
						if (!records.isEmpty()) {
							throw new CommandException("Level " + level + " entry follows a complete record: line "
									+ entry.line() + ": " + entry.text());
						}
						synthetic = new CobolItem();
						synthetic.level = 1;
						synthetic.name = defaultRecordName;
						synthetic.line = entry.line();
						records.add(synthetic);
					}
					stack.push(synthetic);
				}
				CobolItem parent = stack.peek();
				if (parent.level == 77 || !parent.isGroup()) {
					throw new CommandException("Elementary item " + parent.getName()
							+ " cannot have subordinate items: line " + entry.line());
				}
				item.parent = parent;
				parent.children.add(item);
			}
			stack.push(item);
			last = item;
		}
		return records;
	}

	private CobolItem item(int level, List<String> tokens, Entry entry) {
		CobolItem item = new CobolItem();
		item.level = level;
		item.line = entry.line();
		item.remarks = entry.remarks();
		int i = 1;
		if (i < tokens.size() && !CLAUSES.contains(tokens.get(i))) {
			String name = tokens.get(i++);
			if (!NAME.matcher(name).matches()) {
				throw new CommandException("Invalid COBOL data name " + name + ": line " + entry.line());
			}
			item.name = name.equals("FILLER") ? null : name;
		}
		while (i < tokens.size()) {
			String token = tokens.get(i++);
			switch (token) {
			case "REDEFINES" -> item.redefines = required(tokens, i++, token, entry);
			case "PIC", "PICTURE" -> {
				if ("IS".equals(at(tokens, i))) {
					i++;
				}
				item.picture = required(tokens, i++, token, entry);
			}
			case "USAGE" -> {
				if ("IS".equals(at(tokens, i))) {
					i++;
				}
				usage(item, required(tokens, i++, token, entry), entry);
			}
			case "OCCURS" -> i = occurs(item, tokens, i, entry);
			case "SIGN" -> {
				if ("IS".equals(at(tokens, i))) {
					i++;
				}
				i = sign(item, tokens, i, entry);
			}
			case "LEADING", "TRAILING" -> i = sign(item, tokens, i - 1, entry);
			case "VALUE", "VALUES" -> i = skipValues(tokens, i);
			case "JUST", "JUSTIFIED" -> {
				if ("RIGHT".equals(at(tokens, i))) {
					i++;
				}
			}
			case "BLANK" -> {
				if ("WHEN".equals(at(tokens, i))) {
					i++;
				}
				i++;
			}
			case "GLOBAL", "EXTERNAL" -> {
			}
			case "SYNC",
					"SYNCHRONIZED" ->
				throw new CommandException(
						"SYNCHRONIZED is not supported because its slack bytes are not computed: line " + entry.line()
								+ ": " + entry.text());
			default -> {
				if (!usage(item, token, null)) {
					throw new CommandException(
							"Unsupported COBOL clause " + token + ": line " + entry.line() + ": " + entry.text());
				}
			}
			}
		}
		return item;
	}

	private CobolItem.Condition condition(List<String> tokens, Entry entry) {
		if (tokens.size() < 3) {
			throw new CommandException("Invalid level 88 entry: line " + entry.line() + ": " + entry.text());
		}
		int i = 2;
		if (!tokens.get(i).equals("VALUE") && !tokens.get(i).equals("VALUES")) {
			throw new CommandException("Level 88 requires VALUE: line " + entry.line() + ": " + entry.text());
		}
		i++;
		if ("IS".equals(at(tokens, i)) || "ARE".equals(at(tokens, i))) {
			i++;
		}
		return new CobolItem.Condition(tokens.get(1), tokens.subList(i, tokens.size()));
	}

	private boolean usage(CobolItem item, String token, Entry entry) {
		Usage usage = switch (token) {
		case "DISPLAY" -> Usage.DISPLAY;
		case "COMP", "COMPUTATIONAL", "COMP-4", "COMPUTATIONAL-4", "BINARY" -> Usage.BINARY;
		case "COMP-3", "COMPUTATIONAL-3", "PACKED-DECIMAL" -> Usage.PACKED_DECIMAL;
		case "COMP-5", "COMPUTATIONAL-5" -> Usage.NATIVE_BINARY;
		case "COMP-1", "COMPUTATIONAL-1" -> Usage.FLOAT_SHORT;
		case "COMP-2", "COMPUTATIONAL-2" -> Usage.FLOAT_LONG;
		case "NATIONAL" -> Usage.NATIONAL;
		case "DISPLAY-1" -> Usage.DISPLAY_1;
		case "INDEX" -> Usage.INDEX;
		case "POINTER", "FUNCTION-POINTER", "PROCEDURE-POINTER" ->
			throw new CommandException("USAGE " + token + " is not supported: line " + item.line);
		default -> null;
		};
		if (usage == null) {
			if (entry != null) {
				throw new CommandException("Unsupported USAGE " + token + ": line " + entry.line());
			}
			return false;
		}
		item.usage = usage;
		item.usageExplicit = true;
		return true;
	}

	private int occurs(CobolItem item, List<String> tokens, int i, Entry entry) {
		String first = required(tokens, i++, "OCCURS", entry);
		if (!NUMBER.matcher(first).matches()) {
			throw new CommandException("OCCURS requires an integer: line " + entry.line() + ": " + entry.text());
		}
		int minimum = Integer.parseInt(first);
		int maximum = minimum;
		if ("TO".equals(at(tokens, i))) {
			String second = required(tokens, i + 1, "OCCURS TO", entry);
			if (!NUMBER.matcher(second).matches()) {
				throw new CommandException("OCCURS TO requires an integer: line " + entry.line());
			}
			maximum = Integer.parseInt(second);
			i += 2;
		}
		if ("TIMES".equals(at(tokens, i))) {
			i++;
		}
		if ("DEPENDING".equals(at(tokens, i))) {
			i++;
			if ("ON".equals(at(tokens, i))) {
				i++;
			}
			item.dependingOn = required(tokens, i++, "DEPENDING ON", entry);
			while (("OF".equals(at(tokens, i)) || "IN".equals(at(tokens, i))) && i + 1 < tokens.size()) {
				i += 2;
			}
		} else if (minimum != maximum) {
			throw new CommandException("OCCURS m TO n requires DEPENDING ON: line " + entry.line());
		}
		if (maximum < 1 || minimum > maximum) {
			throw new CommandException("Invalid OCCURS bounds: line " + entry.line() + ": " + entry.text());
		}
		while (i < tokens.size() && Set.of("ASCENDING", "DESCENDING", "INDEXED").contains(tokens.get(i))) {
			i++;
			while (i < tokens.size() && Set.of("KEY", "IS", "BY").contains(tokens.get(i))) {
				i++;
			}
			while (i < tokens.size() && !CLAUSES.contains(tokens.get(i))) {
				i++;
			}
		}
		item.occursMinimum = minimum;
		item.occursMaximum = maximum;
		return i;
	}

	private int sign(CobolItem item, List<String> tokens, int i, Entry entry) {
		String position = required(tokens, i++, "SIGN", entry);
		if (!position.equals("LEADING") && !position.equals("TRAILING")) {
			throw new CommandException("SIGN requires LEADING or TRAILING: line " + entry.line());
		}
		item.signExplicit = true;
		item.signLeading = position.equals("LEADING");
		if ("SEPARATE".equals(at(tokens, i))) {
			item.signSeparate = true;
			i++;
			if ("CHARACTER".equals(at(tokens, i))) {
				i++;
			}
		}
		return i;
	}

	private int skipValues(List<String> tokens, int i) {
		while (i < tokens.size()
				&& (!CLAUSES.contains(tokens.get(i)) || tokens.get(i).equals("IS") || tokens.get(i).equals("ARE"))) {
			i++;
		}
		return i;
	}

	private static String at(List<String> tokens, int i) {
		return i < tokens.size() ? tokens.get(i) : null;
	}

	private static String required(List<String> tokens, int i, String clause, Entry entry) {
		if (i >= tokens.size()) {
			throw new CommandException(clause + " is incomplete: line " + entry.line() + ": " + entry.text());
		}
		return tokens.get(i);
	}

	/**
	 * Splits an entry into upper-case tokens; literals keep their case and quotes.
	 */
	private static List<String> tokens(String text) {
		List<String> tokens = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		char quote = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quote != 0) {
				current.append(c);
				if (c == quote) {
					quote = 0;
				}
				continue;
			}
			if (c == '\'' || c == '"') {
				quote = c;
				current.append(c);
				continue;
			}
			boolean separator = Character.isWhitespace(c)
					|| (c == ',' || c == ';') && (i + 1 == text.length() || Character.isWhitespace(text.charAt(i + 1)));
			if (separator) {
				add(tokens, current);
			} else {
				current.append(c);
			}
		}
		add(tokens, current);
		return tokens;
	}

	private static void add(List<String> tokens, StringBuilder current) {
		if (!current.isEmpty()) {
			String token = current.toString();
			boolean literal = token.charAt(0) == '\'' || token.charAt(0) == '"';
			tokens.add(literal ? token : token.toUpperCase(Locale.ROOT));
			current.setLength(0);
		}
	}

	// ------------------------------------------------------------------ resolution

	private void resolve(CobolItem record, CobolItem item) {
		if (item.parent != null) {
			if (item.usage == null) {
				item.usage = item.parent.usage;
			}
			if (!item.signExplicit && item.parent.signExplicit) {
				item.signExplicit = true;
				item.signInherited = true;
				item.signLeading = item.parent.signLeading;
				item.signSeparate = item.parent.signSeparate;
			}
		}
		if (item.redefines != null) {
			item.redefinesItem = redefinedItem(item);
			item.redefinesItem.redefinedBy.add(item);
		}
		if (item.dependingOn != null) {
			item.dependingOnItem = record.find(item.dependingOn);
			if (item.dependingOnItem == null) {
				throw new CommandException(
						"OCCURS DEPENDING ON item was not found: " + item.dependingOn + ", item=" + item.getPath());
			}
		}
		if (item.children.isEmpty()) {
			elementary(item);
		} else if (item.picture != null) {
			throw new CommandException("Group item cannot have PICTURE: " + item.getPath());
		}
		for (CobolItem child : item.children) {
			resolve(record, child);
		}
	}

	private CobolItem redefinedItem(CobolItem item) {
		if (item.parent == null) {
			throw new CommandException(
					"REDEFINES of a record is not supported; import the records separately: " + item.getPath());
		}
		List<CobolItem> siblings = item.parent.children;
		int index = siblings.indexOf(item);
		for (int i = index - 1; i >= 0; i--) {
			CobolItem sibling = siblings.get(i);
			if (sibling.redefines == null) {
				if (!item.redefines.equals(sibling.name)) {
					break;
				}
				return sibling;
			}
		}
		throw new CommandException("REDEFINES must name the preceding item at the same level: " + item.getPath()
				+ " REDEFINES " + item.redefines);
	}

	private static final Pattern REPEAT = Pattern.compile("(.)\\((\\d+)\\)");

	private void elementary(CobolItem item) {
		Usage usage = item.usage == null ? Usage.DISPLAY : item.usage;
		item.usage = usage;
		if (usage == Usage.FLOAT_SHORT || usage == Usage.FLOAT_LONG || usage == Usage.INDEX) {
			if (item.picture != null) {
				throw new CommandException(
						"USAGE " + CobolItem.usageText(usage) + " cannot have PICTURE: " + item.getPath());
			}
			item.category = usage == Usage.INDEX ? Category.INDEX : Category.FLOATING_POINT;
			item.signed = usage != Usage.INDEX;
			item.length = usage == Usage.FLOAT_LONG ? 8 : 4;
			return;
		}
		if (item.picture == null) {
			throw new CommandException("Elementary item requires PICTURE: " + item.getPath());
		}
		String picture = expand(item.picture.toUpperCase(Locale.ROOT), item);
		if (picture.matches("S?[9PV]+") && picture.indexOf('9') >= 0) {
			numeric(item, picture, usage);
		} else if (picture.matches("[XA]+")) {
			character(item, Category.ALPHANUMERIC, picture.length(), usage, 1);
		} else if (picture.matches("N+")) {
			if (usage != Usage.DISPLAY && usage != Usage.NATIONAL) {
				throw new CommandException("PIC N requires USAGE NATIONAL: " + item.getPath());
			}
			item.usage = Usage.NATIONAL;
			character(item, Category.NATIONAL, picture.length(), Usage.NATIONAL, 2);
		} else if (picture.matches("G+")) {
			if (usage != Usage.DISPLAY && usage != Usage.DISPLAY_1) {
				throw new CommandException("PIC G requires USAGE DISPLAY-1: " + item.getPath());
			}
			item.usage = Usage.DISPLAY_1;
			character(item, Category.NATIONAL, picture.length(), Usage.DISPLAY_1, 2);
		} else if (picture.matches("[XA9B0/]*[XA][XA9B0/]*")) {
			character(item, Category.ALPHANUMERIC_EDITED, picture.length(), usage, 1);
		} else if (picture.matches("(?:[9ZBVP0/,.+*$-]|CR|DB|E)+")) {
			if (usage != Usage.DISPLAY) {
				throw new CommandException("Numeric-edited item requires USAGE DISPLAY: " + item.getPath());
			}
			if (item.signExplicit && !item.signInherited) {
				throw new CommandException("SIGN clause requires a signed USAGE DISPLAY item: " + item.getPath());
			}
			clearSign(item);
			item.category = Category.NUMERIC_EDITED;
			int length = picture.replace("V", "").replace("P", "").length();
			item.digits = length;
			item.length = length;
		} else {
			throw new CommandException("Unsupported PICTURE " + item.picture + ": " + item.getPath());
		}
	}

	private static void character(CobolItem item, Category category, int characters, Usage usage, int width) {
		if (usage != Usage.DISPLAY && usage != Usage.NATIONAL && usage != Usage.DISPLAY_1) {
			throw new CommandException(
					"USAGE " + CobolItem.usageText(usage) + " requires a numeric PICTURE: " + item.getPath());
		}
		if (item.signExplicit && !item.signInherited) {
			throw new CommandException("SIGN clause requires a signed USAGE DISPLAY item: " + item.getPath());
		}
		clearSign(item);
		item.category = category;
		item.digits = characters;
		item.length = characters * width;
	}

	private static void numeric(CobolItem item, String picture, Usage usage) {
		item.category = Category.NUMERIC;
		item.signed = picture.startsWith("S");
		String body = item.signed ? picture.substring(1) : picture;
		if (body.indexOf('V') != body.lastIndexOf('V')) {
			throw new CommandException("PICTURE has more than one V: " + item.getPath());
		}
		int nines = count(body, '9');
		int ps = count(body, 'P');
		int point = body.indexOf('V');
		if (ps > 0 && body.matches("V?P+9+")) {
			item.scale = ps + nines;
		} else if (ps > 0 && body.matches("9+P+V?")) {
			item.scale = -ps;
		} else if (ps > 0) {
			throw new CommandException("P must be at the left or right end of PICTURE: " + item.getPath());
		} else {
			item.scale = point < 0 ? 0 : nines - count(body.substring(0, point), '9');
		}
		item.digits = nines;
		if (item.signInherited && (usage != Usage.DISPLAY || !item.signed)) {
			clearSign(item);
		}
		switch (usage) {
		case DISPLAY -> item.length = nines + (item.signSeparate ? 1 : 0);
		case PACKED_DECIMAL -> item.length = nines / 2 + 1;
		case BINARY, NATIVE_BINARY -> {
			if (nines > 18) {
				throw new CommandException("Binary item has more than 18 digits: " + item.getPath());
			}
			item.length = nines <= 4 ? 2 : nines <= 9 ? 4 : 8;
		}
		default -> throw new CommandException(
				"USAGE " + CobolItem.usageText(usage) + " is not supported for numeric PICTURE: " + item.getPath());
		}
		if (item.signExplicit && !item.signInherited && (usage != Usage.DISPLAY || !item.signed)) {
			throw new CommandException("SIGN clause requires a signed USAGE DISPLAY item: " + item.getPath());
		}
		if (item.signInherited && (usage != Usage.DISPLAY || !item.signed)) {
			clearSign(item);
		}
	}

	private static void clearSign(CobolItem item) {
		item.signExplicit = false;
		item.signInherited = false;
		item.signLeading = false;
		item.signSeparate = false;
	}

	private static String expand(String picture, CobolItem item) {
		StringBuilder builder = new StringBuilder();
		Matcher matcher = REPEAT.matcher(picture);
		int last = 0;
		while (matcher.find()) {
			builder.append(picture, last, matcher.start());
			int count = Integer.parseInt(matcher.group(2));
			if (count < 1) {
				throw new CommandException("Invalid PICTURE repetition: " + item.getPath());
			}
			builder.append(matcher.group(1).repeat(count));
			last = matcher.end();
		}
		builder.append(picture.substring(last));
		if (builder.indexOf("(") >= 0 || builder.indexOf(")") >= 0) {
			throw new CommandException("Invalid PICTURE " + picture + ": " + item.getPath());
		}
		return builder.toString();
	}

	private static int count(String value, char c) {
		return (int) value.chars().filter(ch -> ch == c).count();
	}

	// ------------------------------------------------------------------ layout

	private int layout(CobolItem item, int offset) {
		item.offset = offset;
		if (!item.isGroup()) {
			return item.length;
		}
		int current = offset;
		for (CobolItem child : item.children) {
			if (child.redefinesItem != null) {
				layout(child, child.redefinesItem.offset);
				if (child.getTotalLength() > child.redefinesItem.getTotalLength()) {
					throw new CommandException("Redefining item is longer than the redefined item: " + child.getPath()
							+ " (" + child.getTotalLength() + " > " + child.redefinesItem.getTotalLength() + ")");
				}
				continue;
			}
			layout(child, current);
			current += child.getTotalLength();
		}
		item.length = current - offset;
		if (item.length == 0) {
			throw new CommandException("Group item has no storage: " + item.getPath());
		}
		return item.length;
	}

	/**
	 * OCCURS DEPENDING ON is supported only when nothing follows it in the record.
	 */
	private void validateDependingOn(CobolItem item) {
		for (CobolItem child : item.children) {
			validateDependingOn(child);
		}
		if (item.dependingOnItem == null) {
			return;
		}
		if (item.dependingOnItem.isGroup() || item.dependingOnItem.category != Category.NUMERIC
				|| item.dependingOnItem.scale != 0) {
			throw new CommandException(
					"OCCURS DEPENDING ON item must be an integer: " + item.dependingOnItem.getPath());
		}
		if (item.dependingOnItem.offset >= item.offset) {
			throw new CommandException("OCCURS DEPENDING ON item must precede the table: " + item.getPath());
		}
		for (CobolItem current = item; current.parent != null; current = current.parent) {
			List<CobolItem> siblings = current.parent.children;
			for (CobolItem sibling : siblings.subList(siblings.indexOf(current) + 1, siblings.size())) {
				if (sibling.redefinesItem == null) {
					throw new CommandException("Items after OCCURS DEPENDING ON are not supported: " + item.getPath()
							+ " is followed by " + sibling.getPath());
				}
			}
			if (current.parent.occursMaximum != null) {
				throw new CommandException(
						"OCCURS DEPENDING ON inside another OCCURS is not supported: " + item.getPath());
			}
		}
	}
}
