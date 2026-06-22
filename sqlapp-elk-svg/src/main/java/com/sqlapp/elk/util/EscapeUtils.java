package com.sqlapp.elk.util;

public class EscapeUtils {
	public static String escapeXml(String s) {
		return s == null ? ""
				: s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
						"&apos;");
	}
}
