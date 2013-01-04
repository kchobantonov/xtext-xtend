/*******************************************************************************
 * Copyright (c) 2013 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.xtend.core.macro.declaration

import org.eclipse.xtend.lib.macro.declaration.PrimitiveType
import org.eclipse.xtend.lib.macro.declaration.TypeParameterDeclaration
import org.eclipse.xtend.lib.macro.declaration.Visibility
import org.eclipse.xtend.lib.macro.declaration.VoidType
import org.eclipse.xtend.lib.macro.type.AnyTypeReference
import org.eclipse.xtend.lib.macro.type.ArrayTypeReference
import org.eclipse.xtend.lib.macro.type.ParameterizedTypeReference
import org.eclipse.xtend.lib.macro.type.TypeReference
import org.eclipse.xtend.lib.macro.type.UnknownTypeReference
import org.eclipse.xtend.lib.macro.type.WildCardTypeReference
import org.eclipse.xtext.common.types.JvmAnyTypeReference
import org.eclipse.xtext.common.types.JvmGenericArrayTypeReference
import org.eclipse.xtext.common.types.JvmLowerBound
import org.eclipse.xtext.common.types.JvmParameterizedTypeReference
import org.eclipse.xtext.common.types.JvmPrimitiveType
import org.eclipse.xtext.common.types.JvmTypeParameter
import org.eclipse.xtext.common.types.JvmTypeReference
import org.eclipse.xtext.common.types.JvmUnknownTypeReference
import org.eclipse.xtext.common.types.JvmUpperBound
import org.eclipse.xtext.common.types.JvmVoid
import org.eclipse.xtext.common.types.JvmWildcardTypeReference
import org.eclipse.xtext.xbase.compiler.StringBuilderBasedAppendable
import org.eclipse.xtext.xbase.compiler.TypeReferenceSerializer

abstract class AbstractDeclarationImpl<T> {
	@Property T delegate
	@Property CompilationUnitImpl compilationUnit
}

abstract class JvmTypeReferenceImpl<T extends JvmTypeReference> extends AbstractDeclarationImpl<T> implements TypeReference {
	
	@Property TypeReferenceSerializer serializer
	
	override getType() {
		return null
	}
	
	override toString() {
		val appendable  =new StringBuilderBasedAppendable
		serializer.serialize(delegate, compilationUnit.xtendFile, appendable)
		return appendable.toString
	}
	
}


class ParameterizedTypeReferenceImpl extends JvmTypeReferenceImpl<JvmParameterizedTypeReference> implements ParameterizedTypeReference {
	
	override getActualTypeArguments() {
		delegate.arguments.map[compilationUnit.toTypeReference(it)]
	}
	
	override getType() {
		compilationUnit.toType(delegate.type)
	}
	
}

class WildCardTypeReferenceImpl extends JvmTypeReferenceImpl<JvmWildcardTypeReference> implements WildCardTypeReference {
	
	override getLowerBound() {
		// TODO null or AnyTypeReference?
		compilationUnit.toTypeReference(delegate.constraints.filter(typeof(JvmLowerBound)).head?.typeReference) as ParameterizedTypeReference
	}
	
	override getUpperBound() {
		// TODO null or Object?
		compilationUnit.toTypeReference(delegate.constraints.filter(typeof(JvmUpperBound)).head?.typeReference) as ParameterizedTypeReference
	}
	
}

class ArrayTypeReferenceImpl extends JvmTypeReferenceImpl<JvmGenericArrayTypeReference> implements ArrayTypeReference {
	
	override getDimensions() {
		delegate.dimensions
	}

	override getComponentType() {
		compilationUnit.toTypeReference(delegate.componentType)
	}
	
}

class AnyTypeReferenceImpl extends JvmTypeReferenceImpl<JvmAnyTypeReference> implements AnyTypeReference {
}

class UnknownTypeReferenceImpl extends JvmTypeReferenceImpl<JvmUnknownTypeReference> implements UnknownTypeReference {
}

// types

class VoidTypeImpl extends AbstractDeclarationImpl<JvmVoid> implements VoidType {
	
	override getName() {
		'void'
	}
} 

class PrimitiveTypeImpl extends AbstractDeclarationImpl<JvmPrimitiveType> implements PrimitiveType {

	override getKind() {
		switch name {
			case 'boolean' : PrimitiveType$Kind::BOOLEAN
			case 'int' : PrimitiveType$Kind::INT
			case 'char' : PrimitiveType$Kind::CHAR
			case 'double' : PrimitiveType$Kind::DOUBLE
			case 'long' : PrimitiveType$Kind::LONG
			case 'short' : PrimitiveType$Kind::SHORT
			case 'float' : PrimitiveType$Kind::FLOAT
			case 'byte' : PrimitiveType$Kind::BYTE
		}
	}
	
	override getName() {
		delegate.identifier
	}
	
}

class TypeParameterDeclartionImpl extends AbstractDeclarationImpl<JvmTypeParameter> implements TypeParameterDeclaration {
	
	override getUpperBounds() {
		delegate.constraints.filter(typeof(JvmUpperBound)).map[compilationUnit.toTypeReference(typeReference)].toList
	}
	
	override getMembers() {
		emptyList
	}
	
	override getPackageName() {
		null
	}
	
	override getSimpleName() {
		delegate.name
	}
	
	override getName() {
		delegate.name
	}
	
	override getDeclaringType() {
		//TODO this one might be contained in an Xtend element or a JVM element
		throw new UnsupportedOperationException("Auto-generated function stub")
	}
	
	override getDocComment() {
		throw new UnsupportedOperationException("Auto-generated function stub")
	}
	
	override getVisibility() {
		Visibility::PRIVATE
	}
	
}
