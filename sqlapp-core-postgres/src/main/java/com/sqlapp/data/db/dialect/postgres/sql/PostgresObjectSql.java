/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.ArrayList;
import java.util.List;

/** Small lexer for the reader's deparsed DDL; keywords inside expressions are opaque. */
final class PostgresObjectSql {
	private record Token(String text, int start, int end, int depth) { }
	private final String sql;
	private final List<Token> tokens = new ArrayList<>();

	PostgresObjectSql(String sql) {
		this.sql = sql.strip();
		int depth = 0;
		for (int i = 0; i < this.sql.length();) {
			char c = this.sql.charAt(i);
			if (Character.isWhitespace(c)) { i++; continue; }
			int start = i++;
			if (c == '\'' || c == '"') {
				boolean escape = c == '\'' && start > 0 && (this.sql.charAt(start-1)=='E' || this.sql.charAt(start-1)=='e');
				boolean closed = false;
				while (i < this.sql.length()) {
					char next = this.sql.charAt(i++);
					if (escape && next == '\\') { i++; continue; }
					if (next == c) {
						if (i < this.sql.length() && this.sql.charAt(i) == c) { i++; }
						else { closed = true; break; }
					}
				}
				if (!closed) { throw new IllegalArgumentException("Unclosed SQL quote"); }
			} else if (c == '$' && this.sql.substring(start).matches("(?s)^\\$[a-zA-Z_0-9]*\\$.*")) {
				int tagEnd = this.sql.indexOf('$', i);
				String tag = this.sql.substring(start, tagEnd+1);
				int end = this.sql.indexOf(tag, tagEnd+1);
				if (end < 0) { throw new IllegalArgumentException("Unclosed SQL dollar quote"); }
				i = end + tag.length();
			} else if (Character.isLetterOrDigit(c) || c == '_') {
				while (i < this.sql.length() && (Character.isLetterOrDigit(this.sql.charAt(i)) || "_$".indexOf(this.sql.charAt(i))>=0)) { i++; }
			} else if (c == '-' && i < this.sql.length() && this.sql.charAt(i)=='-' || c=='/' && i<this.sql.length() && this.sql.charAt(i)=='*') {
				throw new IllegalArgumentException("Comments are not accepted in definition-backed ALTER input");
			}
			if (c == ')' || c == ']') { depth--; }
			if (depth < 0) { throw new IllegalArgumentException("Unbalanced SQL parentheses"); }
			tokens.add(new Token(this.sql.substring(start,i),start,i,depth));
			if (c == '(' || c == '[') { depth++; }
		}
		if (depth != 0) { throw new IllegalArgumentException("Unbalanced SQL parentheses"); }
	}

	int find(String words) {
		String[] parts = words.split(" ");
		for (int i=0;i<=tokens.size()-parts.length;i++) {
			if(i>0 && tokens.get(i-1).text().equals(".") || i+parts.length<tokens.size() && tokens.get(i+parts.length).text().equals(".")) continue;
			boolean match=true;
			for (int j=0;j<parts.length;j++) {
				Token t=tokens.get(i+j);
				if (t.depth()!=0 || !t.text().equalsIgnoreCase(parts[j])) { match=false; break; }
			}
			if(match) return tokens.get(i).start();
		}
		return -1;
	}

	String before(String keyword) { int p=find(keyword); return p<0 ? sql : sql.substring(0,p).strip(); }
	String after(String keyword) {
		int p=find(keyword);
		if(p<0) return "";
		int count=keyword.split(" ").length;
		for(int i=0;i<tokens.size();i++) if(tokens.get(i).start()==p) return sql.substring(tokens.get(i+count-1).end()).strip();
		throw new IllegalStateException();
	}
	String clause(String keyword, String... following) {
		String rest=after(keyword);
		for(String next:following) rest=new PostgresObjectSql(rest).before(next);
		return rest;
	}
	String group() {
		int start=find("(");
		if(start<0) return "";
		for(Token t:tokens) if(t.start()>start && t.depth()==0 && t.text().equals(")")) return sql.substring(start+1,t.start()).strip();
		throw new IllegalArgumentException("Missing SQL group");
	}
	List<String> split(String separator) {
		var result=new ArrayList<String>();
		int start=0;
		for(Token t:tokens) if(t.depth()==0 && t.text().equals(separator)) {
			String part=sql.substring(start,t.start()).strip();
			if(!part.isEmpty()) result.add(part);
			start=t.end();
		}
		String part=sql.substring(start).strip();
		if(!part.isEmpty()) result.add(part);
		return result;
	}
	String first() { return tokens.isEmpty() ? "" : tokens.get(0).text(); }
	String last() { return tokens.isEmpty() ? "" : tokens.getLast().text(); }
	String rest() { return tokens.isEmpty() ? "" : sql.substring(tokens.get(0).end()).strip(); }
	String skip(int count) {
		if(tokens.size()<count) throw new IllegalArgumentException("Incomplete object definition");
		return sql.substring(tokens.get(count-1).end()).strip();
	}
	static String identifier(String text) {
		return text.startsWith("\"") ? text.substring(1,text.length()-1).replace("\"\"","\"") : text.toLowerCase(java.util.Locale.ROOT);
	}
}
