/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.pli;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.command.host.pli.PliImportConfiguration.CommentPlacement;
import com.sqlapp.exceptions.CommandException;

/**
 * Parses PL/I level declarations.
 *
 * <p>
 * Only {@code DCL}/{@code DECLARE} statements with level numbers, and
 * statements that start with a level number (copybook fragments), are read.
 * Other statements are skipped. Attribute problems of individual items are
 * recorded on the item and reported only when the item is imported.
 * </p>
 */
final class PliDeclarationParser {

	private static final Pattern ITEM_HEAD = Pattern
			.compile("(?s)^(\\d+)\\s*(\\*|[\\p{L}_$#@][\\p{L}\\p{N}_$#@]*)(.*)$");
	private static final Pattern DECLARE = Pattern.compile("(?s)^(?:DCL|DECLARE)(?![\\p{L}\\p{N}_$#@])\\s*(.*)$");
	private static final Pattern BOUNDS = Pattern.compile("^\\s*([+-]?\\d+)\\s*(?::\\s*([+-]?\\d+)\\s*)?$");
	private static final Pattern PRECISION = Pattern.compile("^\\s*(\\d+)\\s*(?:,\\s*([+-]?\\d+)\\s*)?$");
	private static final Pattern LENGTH = Pattern.compile("^\\s*(\\d+)\\s*$");
	private static final Pattern SILENT_PREPROCESSOR = Pattern.compile("^%\\s*(PAGE|SKIP|PRINT|NOPRINT)\\b.*",
			Pattern.DOTALL);

	private static final Set<String> IGNORED = Set.of("STATIC", "AUTOMATIC", "AUTO", "CONTROLLED", "CTL", "EXTERNAL",
			"EXT", "INTERNAL", "INT", "BYADDR", "BYVALUE", "NATIVE", "NONNATIVE", "ASSIGNABLE", "NONASSIGNABLE",
			"NONASGN", "NORMAL", "ABNORMAL", "CONNECTED", "CONN", "NONCONNECTED", "NONCONN", "BIGENDIAN",
			"LITTLEENDIAN", "HEXADEC", "IEEE", "REAL", "PARAMETER", "PARM");
	private static final Set<String> IGNORED_WITH_ARGUMENT = Set.of("INIT", "INITIAL", "BASED", "DEFINED", "DEF",
			"POSITION", "POS", "VALUE");
	private static final Set<String> NOT_MAPPABLE = Set.of("POINTER", "PTR", "OFFSET", "HANDLE", "AREA", "ENTRY",
			"FILE", "LABEL", "FORMAT", "ORDINAL", "TYPE", "CONDITION", "COND", "TASK", "EVENT", "BUILTIN", "GENERIC",
			"RETURNS", "COMPLEX", "CPLX", "UNION", "LIKE");

	private final CommentPlacement placement;
	private final int leftMargin;
	private final int rightMargin;

	PliDeclarationParser(CommentPlacement placement, List<Integer> margins) {
		this.placement = placement == null ? CommentPlacement.AUTO : placement;
		if (margins == null || margins.isEmpty()) {
			this.leftMargin = 1;
			this.rightMargin = Integer.MAX_VALUE;
		} else {
			this.leftMargin = margins.get(0);
			this.rightMargin = margins.get(1);
		}
	}

	record ParseResult(List<PliItem> declarations, List<String> warnings) {
	}

	private record Comment(String text, int startLine, int endLine, boolean codeBeforeOnLine) {
	}

	private static final class Fragment {
		private final StringBuilder text = new StringBuilder();
		private final List<Comment> comments = new ArrayList<>();
		private int startLine = -1;
		private int endLine;

		private boolean isBlank() {
			return text.toString().isBlank();
		}
	}

	private record Event(Fragment fragment, Comment comment, boolean statementEnd) {
	}

	private enum Mode {
		DECLARE, SKIP
	}

	ParseResult parse(String source) {
		String text = applyMargins(source);
		Set<Integer> blankLines = blankLines(text);
		List<Event> events = scan(text);
		List<PliItem> declarations = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		Deque<PliItem> stack = new ArrayDeque<>();
		List<Comment> pending = new ArrayList<>();
		PliItem lastTarget = null;
		int lastFragmentEndLine = -1;
		Mode mode = null;
		int skippedStatements = 0;
		for (Event event : events) {
			if (event.statementEnd) {
				stack.clear();
				mode = null;
				continue;
			}
			if (event.comment != null) {
				String remarks = normalizeComment(event.comment.text);
				if (remarks.isEmpty()) {
					continue;
				}
				switch (placement) {
				case TRAILING -> {
					if (lastTarget != null) {
						lastTarget.remarks = append(lastTarget.remarks, remarks);
					} else {
						pending.add(event.comment);
					}
				}
				case LEADING -> pending.add(event.comment);
				case AUTO -> {
					if (event.comment.codeBeforeOnLine && event.comment.startLine == lastFragmentEndLine) {
						if (lastTarget != null) {
							lastTarget.remarks = append(lastTarget.remarks, remarks);
						}
					} else {
						pending.add(event.comment);
					}
				}
				}
				continue;
			}
			Fragment fragment = event.fragment;
			String code = fragment.text.toString().trim().toUpperCase(Locale.ROOT);
			lastFragmentEndLine = fragment.endLine;
			lastTarget = null;
			if (mode == null) {
				stack.clear();
				Matcher declare = DECLARE.matcher(code);
				if (declare.matches()) {
					mode = Mode.DECLARE;
					code = declare.group(1).trim();
				} else if (!code.isEmpty() && Character.isDigit(code.charAt(0))) {
					mode = Mode.DECLARE;
				} else {
					mode = Mode.SKIP;
					if (code.startsWith("%")) {
						if (!SILENT_PREPROCESSOR.matcher(code).matches()) {
							warnings.add("Preprocessor statement is not expanded (line " + fragment.startLine + "): "
									+ abbreviate(code));
						}
					} else if (!code.startsWith("*PROCESS") && !code.startsWith("PROCESS")) {
						skippedStatements++;
					}
				}
			}
			if (mode == Mode.SKIP) {
				pending.clear();
				continue;
			}
			if (code.isEmpty()) {
				continue;
			}
			Matcher matcher = ITEM_HEAD.matcher(code);
			if (!matcher.matches()) {
				String message = "Declaration without a level number is skipped (line " + fragment.startLine + "): "
						+ abbreviate(code);
				if (stack.isEmpty()) {
					warnings.add(message);
				} else {
					stack.peekLast().errors.add(
							"Unsupported declaration fragment (line " + fragment.startLine + "): " + abbreviate(code));
				}
				pending.clear();
				continue;
			}
			PliItem item = new PliItem();
			item.level = Integer.parseInt(matcher.group(1));
			item.name = "*".equals(matcher.group(2)) ? null : matcher.group(2);
			item.line = fragment.startLine;
			parseAttributes(item, matcher.group(3));
			while (!stack.isEmpty() && stack.peek().level >= item.level) {
				stack.pop();
			}
			if (stack.isEmpty()) {
				declarations.add(item);
			} else {
				PliItem parent = stack.peek();
				if (parent.type != null) {
					parent.root().errors.add("Item " + parent.location()
							+ " has data attributes but contains members; check the level numbers.");
				}
				item.parent = parent;
				parent.children.add(item);
			}
			stack.push(item);
			for (Comment comment : pending) {
				if (placement != CommentPlacement.AUTO || !separated(comment, fragment.startLine, blankLines)) {
					item.remarks = append(item.remarks, normalizeComment(comment.text));
				}
			}
			pending.clear();
			for (Comment comment : fragment.comments) {
				item.remarks = append(item.remarks, normalizeComment(comment.text));
			}
			lastTarget = item;
		}
		if (skippedStatements > 0) {
			warnings.add(skippedStatements + " non-declaration statement(s) were skipped.");
		}
		for (PliItem declaration : declarations) {
			validateStructure(declaration);
		}
		return new ParseResult(declarations, warnings);
	}

	private void validateStructure(PliItem item) {
		if (item.type == null && item.children.isEmpty() && item.unsupported == null) {
			item.unsupported = "has neither data attributes nor members";
		}
		for (PliItem child : item.children) {
			validateStructure(child);
		}
	}

	private boolean separated(Comment comment, int startLine, Set<Integer> blankLines) {
		for (int line = comment.endLine + 1; line < startLine; line++) {
			if (blankLines.contains(line)) {
				return true;
			}
		}
		return false;
	}

	private List<Event> scan(String text) {
		List<Event> events = new ArrayList<>();
		Fragment current = new Fragment();
		boolean codeOnLine = false;
		int line = 1;
		int depth = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\n') {
				line++;
				codeOnLine = false;
				current.text.append(' ');
				continue;
			}
			if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
				int end = text.indexOf("*/", i + 2);
				if (end < 0) {
					throw new CommandException("Unterminated PL/I comment starting at line " + line + ".");
				}
				String body = text.substring(i + 2, end);
				int endLine = line + (int) body.chars().filter(ch -> ch == '\n').count();
				Comment comment = new Comment(body, line, endLine, codeOnLine);
				if (current.isBlank()) {
					events.add(new Event(null, comment, false));
				} else {
					current.comments.add(comment);
				}
				current.text.append(' ');
				line = endLine;
				i = end + 1;
				continue;
			}
			if (c == '\'' || c == '"') {
				int end = i + 1;
				while (true) {
					if (end >= text.length()) {
						throw new CommandException("Unterminated PL/I string literal starting at line " + line + ".");
					}
					if (text.charAt(end) == c) {
						if (end + 1 < text.length() && text.charAt(end + 1) == c) {
							end += 2;
							continue;
						}
						break;
					}
					end++;
				}
				if (current.startLine < 0) {
					current.startLine = line;
				}
				String literal = text.substring(i, end + 1);
				current.text.append(literal.replace('\n', ' '));
				line += (int) literal.chars().filter(ch -> ch == '\n').count();
				codeOnLine = true;
				i = end;
				continue;
			}
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth = Math.max(0, depth - 1);
			}
			if ((c == ',' && depth == 0) || c == ';') {
				codeOnLine = true;
				if (!current.isBlank()) {
					current.endLine = line;
					events.add(new Event(current, null, false));
				}
				if (c == ';') {
					events.add(new Event(null, null, true));
					depth = 0;
				}
				current = new Fragment();
				continue;
			}
			if (!Character.isWhitespace(c)) {
				codeOnLine = true;
				if (current.startLine < 0) {
					current.startLine = line;
				}
			}
			current.text.append(c);
		}
		if (!current.isBlank()) {
			current.endLine = line;
			events.add(new Event(current, null, false));
			events.add(new Event(null, null, true));
		}
		return events;
	}

	private void parseAttributes(PliItem item, String text) {
		List<Token> tokens;
		try {
			tokens = tokenize(text);
		} catch (IllegalArgumentException e) {
			item.unsupported = e.getMessage();
			return;
		}
		String stringKind = null;
		String stringLength = null;
		PliDataType.Varying varying = PliDataType.Varying.NONE;
		String scale = null;
		String base = null;
		String precision = null;
		Boolean signed = null;
		String picture = null;
		for (int i = 0; i < tokens.size(); i++) {
			Token token = tokens.get(i);
			if (token.paren != null) {
				if (i == 0) {
					dimension(item, token.paren);
					continue;
				}
				unsupported(item, "unexpected parenthesized value (" + token.paren + ")");
				continue;
			}
			if (token.string != null) {
				unsupported(item, "unexpected string literal");
				continue;
			}
			String word = token.word;
			String argument = i + 1 < tokens.size() ? tokens.get(i + 1).paren : null;
			if (argument != null) {
				i++;
			}
			switch (word) {
			case "DIM", "DIMENSION" -> {
				if (argument == null) {
					unsupported(item, "DIMENSION requires bounds");
				} else {
					dimension(item, argument);
				}
			}
			case "CHAR", "CHARACTER", "BIT", "GRAPHIC", "G", "WIDECHAR", "WCHAR" -> {
				if (stringKind != null) {
					unsupported(item, "multiple string attributes");
				}
				stringKind = switch (word) {
				case "CHAR", "CHARACTER" -> "CHAR";
				case "G" -> "GRAPHIC";
				case "WCHAR" -> "WIDECHAR";
				default -> word;
				};
				stringLength = argument == null ? "1" : argument;
			}
			case "VARYING", "VAR" -> varying = PliDataType.Varying.VARYING;
			case "VARYINGZ", "VARZ" -> varying = PliDataType.Varying.VARYINGZ;
			case "NONVARYING", "NONVAR" -> varying = PliDataType.Varying.NONE;
			case "FIXED", "FLOAT" -> {
				scale = word;
				precision = argument == null ? precision : argument;
			}
			case "BIN", "BINARY" -> {
				base = "BIN";
				precision = argument == null ? precision : argument;
			}
			case "DEC", "DECIMAL" -> {
				base = "DEC";
				precision = argument == null ? precision : argument;
			}
			case "PRECISION", "PREC" -> {
				if (argument == null) {
					unsupported(item, "PRECISION requires a value");
				}
				precision = argument;
			}
			case "SIGNED" -> signed = Boolean.TRUE;
			case "UNSIGNED" -> signed = Boolean.FALSE;
			case "PIC", "PICTURE" -> {
				if (argument != null || i + 1 >= tokens.size() || tokens.get(i + 1).string == null) {
					unsupported(item, "PICTURE requires a string literal");
				} else {
					picture = tokens.get(++i).string;
				}
			}
			case "ALIGNED" -> item.aligned = Boolean.TRUE;
			case "UNALIGNED", "UNAL" -> item.aligned = Boolean.FALSE;
			default -> {
				if (IGNORED.contains(word) || IGNORED_WITH_ARGUMENT.contains(word)) {
					break;
				}
				if (NOT_MAPPABLE.contains(word)) {
					unsupported(item, "attribute " + word + " is not supported");
				} else {
					unsupported(item, "unknown attribute " + word);
				}
			}
			}
		}
		boolean arithmetic = scale != null || base != null || precision != null || signed != null;
		int kinds = (stringKind != null ? 1 : 0) + (arithmetic ? 1 : 0) + (picture != null ? 1 : 0);
		if (kinds == 0) {
			if (varying != PliDataType.Varying.NONE) {
				unsupported(item, "VARYING requires a string attribute");
			}
			return;
		}
		if (kinds > 1) {
			unsupported(item, "conflicting data attributes");
			return;
		}
		if (stringKind != null) {
			stringType(item, stringKind, stringLength, varying);
		} else if (picture != null) {
			if (varying != PliDataType.Varying.NONE) {
				unsupported(item, "VARYING cannot be combined with PICTURE");
				return;
			}
			try {
				item.type = PliDataType.picture(picture.toUpperCase(Locale.ROOT), PliPicture.parse(picture));
			} catch (IllegalArgumentException e) {
				unsupported(item, e.getMessage());
			}
		} else {
			if (varying != PliDataType.Varying.NONE) {
				unsupported(item, "VARYING requires a string attribute");
				return;
			}
			arithmeticType(item, scale == null ? "FLOAT" : scale, base == null ? "DEC" : base, precision, signed);
		}
	}

	private void stringType(PliItem item, String kind, String lengthText, PliDataType.Varying varying) {
		Matcher matcher = LENGTH.matcher(lengthText);
		if (!matcher.matches()) {
			unsupported(item, "string length must be a positive constant: (" + lengthText + ")");
			return;
		}
		long length = Long.parseLong(matcher.group(1));
		if (length <= 0) {
			unsupported(item, "string length must be a positive constant: (" + lengthText + ")");
			return;
		}
		if ("BIT".equals(kind) && varying != PliDataType.Varying.NONE) {
			unsupported(item, "BIT VARYING is not supported");
			return;
		}
		item.type = PliDataType.string(PliDataType.Kind.valueOf(kind), length, varying);
	}

	private void arithmeticType(PliItem item, String scale, String base, String precisionText, Boolean signed) {
		int precision;
		int fraction = 0;
		boolean fractionGiven = false;
		if (precisionText == null) {
			precision = switch (scale + "_" + base) {
			case "FIXED_DEC" -> 5;
			case "FIXED_BIN" -> 15;
			case "FLOAT_DEC" -> 6;
			default -> 21;
			};
		} else {
			Matcher matcher = PRECISION.matcher(precisionText);
			if (!matcher.matches()) {
				unsupported(item, "precision must be constant: (" + precisionText + ")");
				return;
			}
			precision = Integer.parseInt(matcher.group(1));
			if (matcher.group(2) != null) {
				fraction = Integer.parseInt(matcher.group(2));
				fractionGiven = true;
			}
		}
		if (precision <= 0) {
			unsupported(item, "precision must be positive");
			return;
		}
		if (signed != null && !("FIXED".equals(scale) && "BIN".equals(base))) {
			unsupported(item, "SIGNED/UNSIGNED is supported only for FIXED BINARY");
			return;
		}
		switch (scale + "_" + base) {
		case "FIXED_BIN" -> {
			boolean unsigned = Boolean.FALSE.equals(signed);
			if (fraction != 0) {
				unsupported(item, "scaled FIXED BINARY is not supported");
			} else if (precision > (unsigned ? 64 : 63)) {
				unsupported(item, "FIXED BINARY precision is too large: " + precision);
			} else {
				item.type = PliDataType.fixedBinary(precision, unsigned);
			}
		}
		case "FIXED_DEC" -> {
			if (precision > 31) {
				unsupported(item, "FIXED DECIMAL precision is too large: " + precision);
			} else {
				item.type = PliDataType.fixedDecimal(precision, fraction);
			}
		}
		default -> {
			if (fractionGiven) {
				unsupported(item, "FLOAT does not take a scale factor");
			} else if ("BIN".equals(base) ? precision > 53 : precision > 16) {
				unsupported(item, "extended FLOAT is not supported");
			} else {
				item.type = PliDataType.floating(
						"BIN".equals(base) ? PliDataType.Kind.FLOAT_BIN : PliDataType.Kind.FLOAT_DEC, precision);
			}
		}
		}
	}

	private void dimension(PliItem item, String bounds) {
		if (item.occurs != null) {
			unsupported(item, "multiple dimension attributes");
			return;
		}
		String upper = bounds.toUpperCase(Locale.ROOT);
		if (upper.contains("REFER")) {
			unsupported(item, "REFER is not supported");
			return;
		}
		if (upper.contains(",")) {
			unsupported(item, "multi-dimensional arrays are not supported (" + bounds + ")");
			return;
		}
		Matcher matcher = BOUNDS.matcher(bounds);
		if (!matcher.matches()) {
			unsupported(item, "array bounds must be constant (" + bounds + ")");
			return;
		}
		int lower = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(1));
		int high = Integer.parseInt(matcher.group(2) == null ? matcher.group(1) : matcher.group(2));
		if (high < lower) {
			unsupported(item, "array upper bound is lower than the lower bound (" + bounds + ")");
			return;
		}
		item.occurs = high - lower + 1;
		item.lowerBound = lower;
	}

	private void unsupported(PliItem item, String reason) {
		if (item.unsupported == null) {
			item.unsupported = reason;
		}
	}

	private record Token(String word, String paren, String string) {
	}

	private List<Token> tokenize(String text) {
		List<Token> tokens = new ArrayList<>();
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (Character.isLetter(c) || c == '_' || c == '$' || c == '#' || c == '@') {
				int start = i;
				while (i < text.length()
						&& (Character.isLetterOrDigit(text.charAt(i)) || "_$#@".indexOf(text.charAt(i)) >= 0)) {
					i++;
				}
				tokens.add(new Token(text.substring(start, i), null, null));
			} else if (c == '(') {
				int depth = 0;
				int start = i;
				char quote = 0;
				for (; i < text.length(); i++) {
					char ch = text.charAt(i);
					if (quote != 0) {
						if (ch == quote) {
							quote = 0;
						}
					} else if (ch == '\'' || ch == '"') {
						quote = ch;
					} else if (ch == '(') {
						depth++;
					} else if (ch == ')' && --depth == 0) {
						break;
					}
				}
				if (i >= text.length()) {
					throw new IllegalArgumentException("unbalanced parentheses");
				}
				tokens.add(new Token(null, text.substring(start + 1, i), null));
				i++;
			} else if (c == '\'' || c == '"') {
				StringBuilder value = new StringBuilder();
				int j = i + 1;
				while (true) {
					if (j >= text.length()) {
						throw new IllegalArgumentException("unterminated string literal");
					}
					char ch = text.charAt(j);
					if (ch == c) {
						if (j + 1 < text.length() && text.charAt(j + 1) == c) {
							value.append(c);
							j += 2;
							continue;
						}
						break;
					}
					value.append(ch);
					j++;
				}
				i = j + 1;
				// Skip a string suffix such as 'FF'X.
				while (i < text.length() && Character.isLetter(text.charAt(i))) {
					i++;
				}
				tokens.add(new Token(null, null, value.toString()));
			} else {
				throw new IllegalArgumentException("unexpected character '" + c + "'");
			}
		}
		return tokens;
	}

	private String applyMargins(String source) {
		String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
		if (leftMargin == 1 && rightMargin == Integer.MAX_VALUE) {
			return normalized;
		}
		StringBuilder builder = new StringBuilder();
		String[] lines = normalized.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			int start = Math.min(leftMargin - 1, line.length());
			int end = Math.min(rightMargin, line.length());
			builder.append(end > start ? line.substring(start, end) : "");
			if (i + 1 < lines.length) {
				builder.append('\n');
			}
		}
		return builder.toString();
	}

	private Set<Integer> blankLines(String text) {
		Set<Integer> result = new HashSet<>();
		String[] lines = text.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].isBlank()) {
				result.add(i + 1);
			}
		}
		return result;
	}

	static String normalizeComment(String body) {
		List<String> lines = new ArrayList<>();
		for (String line : body.split("\n")) {
			String stripped = line.strip().replaceAll("^[*=\\-]+", "").replaceAll("[*=\\-]+$", "").strip();
			if (!stripped.isEmpty()) {
				lines.add(stripped);
			}
		}
		return String.join(" ", lines);
	}

	private static String append(String current, String addition) {
		if (addition == null || addition.isEmpty()) {
			return current;
		}
		return current == null || current.isBlank() ? addition : current + " " + addition;
	}

	private static String abbreviate(String value) {
		String collapsed = value.replaceAll("\\s+", " ").trim();
		return collapsed.length() > 60 ? collapsed.substring(0, 57) + "..." : collapsed;
	}
}
