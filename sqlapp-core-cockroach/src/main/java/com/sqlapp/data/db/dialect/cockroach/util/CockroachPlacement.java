/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.util;

import java.util.regex.Pattern;
import com.sqlapp.data.schemas.Table;

/** Internal SQL boundary handling for placement specifics; no topology inference. */
public final class CockroachPlacement {
	private CockroachPlacement() { }
	private static final String IDENT="(?:[a-zA-Z_][a-zA-Z_0-9$]*|\"(?:[^\"]|\"\")+\")";
	private static final String REGION="(?:"+IDENT+"|'(?:[^']|'')+')";
	private static final Pattern LOCALITY=Pattern.compile("(?:GLOBAL|REGIONAL\\s+BY\\s+TABLE(?:\\s+IN\\s+(?:PRIMARY\\s+REGION|"+REGION+"))?|REGIONAL\\s+BY\\s+ROW(?:\\s+AS\\s+"+IDENT+")?)",Pattern.CASE_INSENSITIVE);
	public static String quote(String name) {
		if(name==null || name.isBlank()) throw new IllegalArgumentException("CockroachDB placement identifier must not be empty");
		return "\""+name.replace("\"","\"\"")+"\"";
	}
	public static String locality(String value) {
		if(value==null || !LOCALITY.matcher(value.trim()).matches()) throw new IllegalArgumentException("COCKROACH_LOCALITY must be GLOBAL, REGIONAL BY TABLE [IN region], or REGIONAL BY ROW [AS column]");
		return value.trim();
	}
	private record Boundary(int locality,int end) { }
	/** Only inspect unquoted tokens outside the table body, before its first semicolon. */
	private static Boundary boundary(String ddl) {
		int depth=0,start=-1;boolean bodySeen=false;
		for(int i=0;i<ddl.length();) {
			char ch=ddl.charAt(i);
			if(ch=='\'' || ch=='"') {
				char quote=ch;i++;
				while(i<ddl.length()) {char q=ddl.charAt(i++);if(q==quote) {if(i<ddl.length() && ddl.charAt(i)==quote) i++;else break;} else if(q=='\\' && quote=='\'' && i<ddl.length()) i++;}
			} else if(ch=='-' && i+1<ddl.length() && ddl.charAt(i+1)=='-') {
				int end=ddl.indexOf('\n',i+2);i=end<0?ddl.length():end+1;
			} else if(ch=='/' && i+1<ddl.length() && ddl.charAt(i+1)=='*') {
				int end=ddl.indexOf("*/",i+2);if(end<0) throw new IllegalArgumentException("Unclosed SQL comment");i=end+2;
			} else if(ch=='$' && ddl.substring(i).matches("(?s)^\\$[a-zA-Z_0-9]*\\$.*")) {
				int tagEnd=ddl.indexOf('$',i+1);String tag=ddl.substring(i,tagEnd+1);int end=ddl.indexOf(tag,tagEnd+1);
				if(end<0) throw new IllegalArgumentException("Unclosed dollar-quoted SQL");i=end+tag.length();
			} else if(ch=='(') {depth++;i++;}
			else if(ch==')') {depth--;if(depth==0) bodySeen=true;i++;}
			else if(ch==';' && depth==0) return new Boundary(start,i);
			else if(Character.isLetter(ch) || ch=='_') {
				int begin=i++;while(i<ddl.length() && (Character.isLetterOrDigit(ddl.charAt(i)) || ddl.charAt(i)=='_' || ddl.charAt(i)=='$')) i++;
				if(bodySeen && depth==0 && ddl.substring(begin,i).equalsIgnoreCase("LOCALITY")) start=begin;
			} else i++;
		}
		return new Boundary(start,ddl.length());
	}
	public static String readLocality(String ddl) {
		var boundary=boundary(ddl);
		return boundary.locality()<0?null:locality(ddl.substring(boundary.locality()+8,boundary.end()));
	}
	/** Explicit specifics override only the LOCALITY suffix, retaining the body and comments. */
	public static String definition(Table table) {
		String ddl=String.join("\n",table.getDefinition());String value=table.getSpecifics().get("COCKROACH_LOCALITY");
		if(value==null) return ddl;
		value=locality(value);var boundary=boundary(ddl);int start=boundary.locality()<0?boundary.end():boundary.locality();
		return ddl.substring(0,start).stripTrailing()+" LOCALITY "+value+ddl.substring(boundary.end());
	}
}
