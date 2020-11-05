/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.schemas.rowiterator;

import java.io.File;
import java.io.IOException;

import com.sqlapp.util.eval.EvalExecutor;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;
import com.sqlapp.util.eval.mvel.MvelUtils;

public class ExpressionConverter {

	private String placeholderPrefix="${";

	private String placeholderSuffix="}";

	private boolean placeholders;

	private File fileDirectory=null;
	
	/**
	 * @return the placeholderPrefix
	 */
	public String getPlaceholderPrefix() {
		return placeholderPrefix;
	}

	/**
	 * @param placeholderPrefix the placeholderPrefix to set
	 */
	public void setPlaceholderPrefix(String placeholderPrefix) {
		this.placeholderPrefix = placeholderPrefix;
	}

	/**
	 * @return the placeholderSuffix
	 */
	public String getPlaceholderSuffix() {
		return placeholderSuffix;
	}

	/**
	 * @param placeholderSuffix the placeholderSuffix to set
	 */
	public void setPlaceholderSuffix(String placeholderSuffix) {
		this.placeholderSuffix = placeholderSuffix;
	}

	/**
	 * @return the placeholders
	 */
	public boolean isPlaceholders() {
		return placeholders;
	}

	/**
	 * @param placeholders the placeholders to set
	 */
	public void setPlaceholders(boolean placeholders) {
		this.placeholders = placeholders;
	}

	/**
	 * @return the fileDirectory
	 */
	public File getFileDirectory() {
		return fileDirectory;
	}

	/**
	 * @param fileDirectory the fileDirectory to set
	 */
	public void setFileDirectory(File fileDirectory) {
		this.fileDirectory = fileDirectory;
		if (fileDirectory!=null){
			MvelUtils.setBasePath(fileDirectory.getAbsolutePath());
		}
	}

	
	/**
	 * 値を解析してファイルデータを置換します。
	 * @param value 解析前の値
	 * @return 変換後の値
	 * @throws IOException 
	 */
	public Object convert(Object value, Object context) throws IOException{
		if (value==null){
			return value;
		}
		if (this.isPlaceholders()){
			return convertInternal(value, context);
		} else{
			return value;
		}
	}

	private CachedMvelEvaluator cachedMvelEvaluator=new CachedMvelEvaluator();

	private Object convertInternal(Object value, Object context) throws IOException{
		if (value==null){
			return value;
		}
		if (!(value instanceof String)){
			return value;
		}
		String text=String.class.cast(value);
		if (text.startsWith(this.getPlaceholderPrefix())){
			if (text.endsWith(this.getPlaceholderSuffix())){
				String expression=text.substring(this.getPlaceholderPrefix().length(), text.length()-this.getPlaceholderSuffix().length());
				MvelUtils.setBasePath(fileDirectory.getAbsolutePath());
				EvalExecutor evalExecutor=cachedMvelEvaluator.getEvalExecutor(expression);
				Object obj=evalExecutor.eval(context);
				return obj;
			}
		}
		return value;
	}
}
