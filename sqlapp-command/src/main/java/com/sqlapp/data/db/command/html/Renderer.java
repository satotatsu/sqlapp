/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command.html;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mvel2.templates.CompiledTemplate;
import org.mvel2.templates.TemplateCompiler;
import org.mvel2.templates.TemplateRuntime;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.eval.EvalExecutor;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;

public class Renderer {

	private CompiledTemplate compiledTemplate=null;

	private String template;

	private RenderOptions renderOptions=new RenderOptions();
	
	public Renderer(){
	}
	
	protected void compile(){
		synchronized(Renderer.class){
			compiledTemplate=TemplateCompiler.compileTemplate(convertInclude(template), renderOptions.getParserContext());
		}
	}

	private static Pattern INCLUDE_PATTERN=Pattern.compile("[ \\t]*<!--\\s*include\\(\\s*(?<filename>[^)]*?)\\s*\\)(?<expression>.*?)?\\s*-->[ \\t]*", Pattern.MULTILINE);

	protected String convertInclude(String value){
		String converted=value;
		converted=convertIncludeInternal(converted);
		while(true){
			Matcher matcher=INCLUDE_PATTERN.matcher(converted);
			if(!matcher.find()){
				return converted;
			}
			converted=convertIncludeInternal(converted);
		}
	}
	
	protected String convertIncludeInternal(String value){
		StringBuilder builder=new StringBuilder();
		Matcher matcher=INCLUDE_PATTERN.matcher(value);
		int pos=0;
		while(matcher.find()){
			int start=matcher.start();
			int end=matcher.end();
			String filename=matcher.group("filename");
			String text=this.readResource(filename);
			text=convertExpression(text, matcher.group("expression"));
			String sub=value.substring(pos, start);
			builder.append(sub);
			builder.append(text);
			pos=end;
		}
		if (builder.length()==0){
			return value;
		}else if (pos<value.length()){
			String sub=value.substring(pos);
			builder.append(sub);
		}
		return builder.toString();
	}

	private String convertExpression(String text, String expression){
		expression=CommonUtils.trim(expression);
		if (CommonUtils.isEmpty(expression)){
			return text;
		}
		EvalExecutor evalExecutor=CachedMvelEvaluator.getInstance().getEvalExecutor("text"+expression);
		ParametersContext context=new ParametersContext();
		context.put("text", text);
		return (String)evalExecutor.eval(context);
	}
	
	public String render(ParametersContext context){
		initializeContext(context);
		return execute(context);
	}
	
	protected void initializeContext(ParametersContext context){
		
	}

	private String execute(ParametersContext context){
		if (compiledTemplate==null){
			compile();
		}
		if (!context.containsKeyInternal("renderOptions")){
			context.put("renderOptions", renderOptions);
		}
		String text=(String)TemplateRuntime.execute(compiledTemplate, context);
		return text;
	}

	public void setTemplate(File file){
		this.template=FileUtils.readText(file, "utf8");
	}

	public void setTemplate(String template){
		this.template=template;
	}

	protected String readResource(String filename){
		InputStream is = FileUtils.getInputStream(this.getClass(), filename);
		String text = FileUtils.readText(is, "utf8");
		return text;
	}
	
	private String templateResource=null;

	public void setTemplateResource(String filename){
		this.templateResource=filename;
		InputStream is = FileUtils.getInputStream(this.getClass(), filename);
		if (is==null){
			throw new RuntimeException(new FileNotFoundException("filename="+filename));
		}
		String text = FileUtils.readText(is, "utf8");
		setTemplate(text);
	}

	
	/**
	 * @return the templateResource
	 */
	public String getTemplateResource() {
		return templateResource;
	}

	/**
	 * @return the compiledTemplate
	 */
	protected CompiledTemplate getCompiledTemplate() {
		return compiledTemplate;
	}

	/**
	 * @return the template
	 */
	protected String getTemplate() {
		return template;
	}

	/**
	 * @param renderOptions the renderOptions to set
	 */
	public void setRenderOptions(RenderOptions renderOptions) {
		this.renderOptions = renderOptions;
	}

	/**
	 * @return the renderOptions
	 */
	protected RenderOptions getRenderOptions() {
		return renderOptions;
	}

}
