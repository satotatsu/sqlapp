/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-graphviz.
 *
 * sqlapp-graphviz is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-graphviz is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-graphviz.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.graphviz;
/**
 * 
 * @author tatsuo satoh
 */
public enum ArrowType {
	box
	,lbox(){
		@Override
		public ArrowType getBaseType(){
			return box;
		}
	}
	,rbox(){
		@Override
		public ArrowType getBaseType(){
			return box;
		}
	}
	,obox(){
		@Override
		public ArrowType getBaseType(){
			return box;
		}
	}
	,olbox(){
		@Override
		public ArrowType getBaseType(){
			return box;
		}
	}
	,orbox(){
		@Override
		public ArrowType getBaseType(){
			return box;
		}
	}
	,crow
	,lcrow(){
		@Override
		public ArrowType getBaseType(){
			return crow;
		}
	}
	,rcrow(){
		@Override
		public ArrowType getBaseType(){
			return crow;
		}
	}
	,curve
	,lcurve(){
		@Override
		public ArrowType getBaseType(){
			return curve;
		}
	}
	,rcurve(){
		@Override
		public ArrowType getBaseType(){
			return curve;
		}
	}
	, diamond
	,ldiamond(){
		@Override
		public ArrowType getBaseType(){
			return diamond;
		}
	}
	,rdiamond(){
		@Override
		public ArrowType getBaseType(){
			return diamond;
		}
	}
	,odiamond(){
		@Override
		public ArrowType getBaseType(){
			return diamond;
		}
	}
	,oldiamond(){
		@Override
		public ArrowType getBaseType(){
			return diamond;
		}
	}
	,ordiamond(){
		@Override
		public ArrowType getBaseType(){
			return diamond;
		}
	}
	, dot
	, invdot
	,odot(){
		@Override
		public ArrowType getBaseType(){
			return dot;
		}
	}
	,invodot(){
		@Override
		public ArrowType getBaseType(){
			return dot;
		}
	}
	, inv
	,linv(){
		@Override
		public ArrowType getBaseType(){
			return inv;
		}
		@Override
		public ArrowType getInvType(){
			return normal;
		}
	}
	,rinv(){
		@Override
		public ArrowType getBaseType(){
			return inv;
		}
	}
	,oinv(){
		@Override
		public ArrowType getBaseType(){
			return inv;
		}
	}
	,olinv(){
		@Override
		public ArrowType getBaseType(){
			return inv;
		}
	}
	,orinv(){
		@Override
		public ArrowType getBaseType(){
			return inv;
		}
	}
	, none
	, normal(){
		@Override
		public ArrowType getInvType(){
			return inv;
		}
	}
	, empty
	, invempty
	,lnormal(){
		@Override
		public ArrowType getBaseType(){
			return normal;
		}
	}
	,rnormal(){
		@Override
		public ArrowType getBaseType(){
			return normal;
		}
	}
	,onormal(){
		@Override
		public ArrowType getBaseType(){
			return normal;
		}
	}
	,olnormal(){
		@Override
		public ArrowType getBaseType(){
			return normal;
		}
	}
	,ornormal(){
		@Override
		public ArrowType getBaseType(){
			return normal;
		}
	}
	, tee
	,ltee(){
		@Override
		public ArrowType getBaseType(){
			return tee;
		}
	}
	,rtee(){
		@Override
		public ArrowType getBaseType(){
			return tee;
		}
	}
	, vee
	,lvee(){
		@Override
		public ArrowType getBaseType(){
			return vee;
		}
	}
	,rvee(){
		@Override
		public ArrowType getBaseType(){
			return vee;
		}
	}
	,;
	
	public ArrowType getBaseType(){
		return null;
	}

	public ArrowType getInvType(){
		return null;
	}

}
