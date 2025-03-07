/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-graphviz.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.graphviz;

/**
 * ArrowType
 * 
 * @author tatsuo satoh
 */
public enum ArrowType {
	/* box */
	box,
	/* lbox */
	lbox() {
		@Override
		public ArrowType getBaseType() {
			return box;
		}
	},
	/* rbox */
	rbox() {
		@Override
		public ArrowType getBaseType() {
			return box;
		}
	},
	/* obox */
	obox() {
		@Override
		public ArrowType getBaseType() {
			return box;
		}
	},
	/* olbox */
	olbox() {
		@Override
		public ArrowType getBaseType() {
			return box;
		}
	},
	/* orbox */
	orbox() {
		@Override
		public ArrowType getBaseType() {
			return box;
		}
	},
	/* rrow */
	crow,
	/* lrow */
	lcrow() {
		@Override
		public ArrowType getBaseType() {
			return crow;
		}
	},
	/* rcrow */
	rcrow() {
		@Override
		public ArrowType getBaseType() {
			return crow;
		}
	},
	/* curve */
	curve,
	/* lcurve */
	lcurve() {
		@Override
		public ArrowType getBaseType() {
			return curve;
		}
	},
	/* rcurve */
	rcurve() {
		@Override
		public ArrowType getBaseType() {
			return curve;
		}
	},
	/* diamond */
	diamond,
	/* ldiamond */
	ldiamond() {
		@Override
		public ArrowType getBaseType() {
			return diamond;
		}
	},
	/* rdiamond */
	rdiamond() {
		@Override
		public ArrowType getBaseType() {
			return diamond;
		}
	},
	/* odiamond */
	odiamond() {
		@Override
		public ArrowType getBaseType() {
			return diamond;
		}
	},
	/* oldiamond */
	oldiamond() {
		@Override
		public ArrowType getBaseType() {
			return diamond;
		}
	},
	/* ordiamond */
	ordiamond() {
		@Override
		public ArrowType getBaseType() {
			return diamond;
		}
	},
	/* dot */
	dot,
	/* invdot */
	invdot() {
		@Override
		public ArrowType getBaseType() {
			return dot;
		}
	},
	/* odot */
	odot() {
		@Override
		public ArrowType getBaseType() {
			return dot;
		}
	},
	/* invodot */
	invodot() {
		@Override
		public ArrowType getBaseType() {
			return dot;
		}
	},
	/* inv */
	inv,
	/* linv */
	linv() {
		@Override
		public ArrowType getBaseType() {
			return inv;
		}

		@Override
		public ArrowType getInvType() {
			return normal;
		}
	},
	/* rinv */
	rinv() {
		@Override
		public ArrowType getBaseType() {
			return inv;
		}
	},
	/* oinv */
	oinv() {
		@Override
		public ArrowType getBaseType() {
			return inv;
		}
	},
	/* olinv */
	olinv() {
		@Override
		public ArrowType getBaseType() {
			return inv;
		}
	},
	/* orinv */
	orinv() {
		@Override
		public ArrowType getBaseType() {
			return inv;
		}
	},
	/* none */
	none,
	/* normal */
	normal,
	/* empty */
	empty,
	/* invempty */
	invempty() {
		@Override
		public ArrowType getBaseType() {
			return empty;
		}
	},
	/* lnormal */
	lnormal() {
		@Override
		public ArrowType getBaseType() {
			return normal;
		}
	},
	/* rnormal */
	rnormal() {
		@Override
		public ArrowType getBaseType() {
			return normal;
		}
	},
	/* onormal */
	onormal() {
		@Override
		public ArrowType getBaseType() {
			return normal;
		}
	},
	/* olnormal */
	olnormal() {
		@Override
		public ArrowType getBaseType() {
			return normal;
		}
	},
	/* ornormal */
	ornormal() {
		@Override
		public ArrowType getBaseType() {
			return normal;
		}
	},
	/* tee */
	tee,
	/* ltee */
	ltee() {
		@Override
		public ArrowType getBaseType() {
			return tee;
		}
	},
	/* rtee */
	rtee() {
		@Override
		public ArrowType getBaseType() {
			return tee;
		}
	},
	/* vee */
	vee,
	/* vee */
	lvee() {
		@Override
		public ArrowType getBaseType() {
			return vee;
		}
	},
	/* rvee */
	rvee() {
		@Override
		public ArrowType getBaseType() {
			return vee;
		}
	},;

	public ArrowType getBaseType() {
		return null;
	}

	public ArrowType getInvType() {
		return null;
	}

}
