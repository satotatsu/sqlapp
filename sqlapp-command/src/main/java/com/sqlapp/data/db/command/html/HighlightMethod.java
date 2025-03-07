/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.html;

import java.util.List;

import com.sqlapp.data.schemas.AssemblyFile;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

public enum HighlightMethod {
	Highlight(){
		@Override
		public boolean isHighlight(){
			return true;
		}
		@Override
		public String getVersion(){
			return "9.12.0";
		}
		@Override
		public String getCommonPath(){
			return "//cdnjs.cloudflare.com/ajax/libs/highlight.js/";
		}
		@Override
		public String[] getJsInternal(){
			return new String[]{"/highlight.min.js"};
		}
		@Override
		protected String[] getCssInternal(){
			return new String[]{"/styles/github.min.css"};
		}
		@Override
		protected String getLanguageInternal(String text){
			if (text.equalsIgnoreCase("cs")){
				return "cs";
			}
			return super.getLanguageInternal(text);
		}
		@Override
		public String loadInitScript(){
			return "hljs.initHighlightingOnLoad();";
		}
	},
	Prism(){
		@Override
		public boolean isPrism(){
			return true;
		}

		@Override
		public String getVersion(){
			return "1.11.0";
		}

		@Override
		public String getCommonPath(){
			return "//cdnjs.cloudflare.com/ajax/libs/prism/";
		}

		@Override
		public String[] getJsInternal(){
			return new String[]{
				"/prism.min.js"
				,"/components/prism-css-extras.min.js"
				,"/components/prism-csharp.min.js"
				,"/components/prism-java.min.js"
				,"/components/prism-sql.min.js"
			};
		}

		@Override
		protected String[] getCssInternal(){
			return new String[]{
				"/themes/prism.min.css"
			};
		}

		@Override
		protected String getLanguageInternal(String text){
			if (text.equalsIgnoreCase("cs")){
				return "csharp";
			}
			return super.getLanguageInternal(text);
		}

		@Override
		public String getLanguagePefix(){
			return "language-";
		}
		
		protected String getPreClassInternal(){
			return "line-numbers";
		}
	},
	;

	public String getCommonPath(){
		return null;
	}

	public String getVersion(){
		return null;
	}

	public String[] getJs(){
		String[] args=new String[getJsInternal().length];
		for(int i=0;i<getJsInternal().length;i++){
			args[i]=getCommonPath()+getVersion()+getJsInternal()[i];
		}
		return args;
	}

	protected String[] getJsInternal(){
		return new String[0];
	}

	public String[] getCss(){
		String[] args=new String[getCssInternal().length];
		for(int i=0;i<getCssInternal().length;i++){
			args[i]=getCommonPath()+getVersion()+getCssInternal()[i];
		}
		return args;
	}

	protected String[] getCssInternal(){
		return new String[0];
	}

	public String getLanguagePefix(){
		return "";
	}

	public boolean isHighlight(){
		return false;
	}

	public boolean isPrism(){
		return false;
	}
	
	public String getLanguage(Object obj){
		return this.getLanguagePefix()+getLanguageInternal(obj);
	}

	protected String getLanguageInternal(Object obj){
		if (obj instanceof AssemblyFile){
			AssemblyFile assemblyFile=(AssemblyFile)obj;
			String extension=FileUtils.getExtension(assemblyFile.getName());
			return getLanguageInternal(extension);
		}else if (obj instanceof List){
			@SuppressWarnings("unchecked")
			List<String> args=(List<String>)obj;
			if (args.size()==0){
				return "sql";
			}
			String first=CommonUtils.first(args);
			if (first.startsWith("<")){
				return "xml";
			}
		}
		return "sql";
	}

	protected String getLanguageInternal(String text){
		if (text.equalsIgnoreCase("cs")){
			return "csharp";
		}else if (text.equalsIgnoreCase("vb")){
			return "vb";
		}else if (text.equalsIgnoreCase("xml")){
			return "xml";
		}
		return "sql";
	}

	public String getPreClass(){
		if (this.getPreClassInternal()!=null){
			return "class=\""+this.getPreClassInternal()+"\"";
		}
		return null;
	}

	protected String getPreClassInternal(){
		return null;
	}
	
	public String loadInitScript(){
		return "";
	}
	
}
