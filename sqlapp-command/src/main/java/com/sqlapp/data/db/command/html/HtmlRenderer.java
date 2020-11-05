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
import java.util.ConcurrentModificationException;

import org.mvel2.templates.CompiledTemplate;
import org.mvel2.templates.TemplateCompiler;
import org.mvel2.templates.TemplateRuntime;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.util.FileUtils;

public class HtmlRenderer extends Renderer{

	private CompiledTemplate compiledLayoutTemplate=null;

	private String layoutTemplate;
	
	public HtmlRenderer(){
		this.setLayoutTemplate(this.readResource("basicLayout.html"));
	}
	
	protected void compile(){
		synchronized(this){
			if (layoutTemplate!=null){
				compiledLayoutTemplate=TemplateCompiler.compileTemplate(convertInclude(layoutTemplate), this.getRenderOptions().getParserContext());
			}
			super.compile();
		}
	}

	
	public String render(ParametersContext context){
		initializeContext(context);
		return execute(context);
	}

	protected void initializeContext(ParametersContext context){
		
	}

	private String execute(ParametersContext context){
		if (this.getCompiledTemplate()==null){
			compile();
		}
		if (!context.containsKeyInternal("renderOptions")){
			context.put("renderOptions", this.getRenderOptions());
		}
		String text=(String)executeTemplate(this.getCompiledTemplate(), context);
		if (compiledLayoutTemplate!=null){
			context.put("body", text);
			String result=(String)executeTemplate(compiledLayoutTemplate, context);
			return result;
		}
		return text;
	}
	
	private String executeTemplate(CompiledTemplate compiled, ParametersContext context) {
		while(true) {
			try {
				String text=(String)TemplateRuntime.execute(compiled, context);
				return text;
			} catch(ConcurrentModificationException e) {
				continue;
			}
		}
	}

	public void setLayoutTemplate(File file){
		this.layoutTemplate=FileUtils.readText(file, "utf8");
	}

	public void setLayoutTemplate(String layoutTemplate){
		this.layoutTemplate=layoutTemplate;
	}

}
