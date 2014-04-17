/*******************************************************************************
 * Copyright (c) 2011 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtend.core.compiler;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.xtend.core.jvmmodel.IXtendJvmAssociations;
import org.eclipse.xtend.core.richstring.AbstractRichStringPartAcceptor;
import org.eclipse.xtend.core.richstring.DefaultIndentationHandler;
import org.eclipse.xtend.core.richstring.RichStringProcessor;
import org.eclipse.xtend.core.xtend.AnonymousClassConstructorCall;
import org.eclipse.xtend.core.xtend.RichString;
import org.eclipse.xtend.core.xtend.RichStringForLoop;
import org.eclipse.xtend.core.xtend.RichStringIf;
import org.eclipse.xtend.core.xtend.RichStringLiteral;
import org.eclipse.xtend.core.xtend.XtendClass;
import org.eclipse.xtend.core.xtend.XtendFormalParameter;
import org.eclipse.xtend.core.xtend.XtendPackage;
import org.eclipse.xtend.core.xtend.XtendVariableDeclaration;
import org.eclipse.xtend2.lib.StringConcatenation;
import org.eclipse.xtend2.lib.StringConcatenationClient;
import org.eclipse.xtext.common.types.JvmDeclaredType;
import org.eclipse.xtext.common.types.JvmFormalParameter;
import org.eclipse.xtext.common.types.JvmGenericType;
import org.eclipse.xtext.common.types.JvmType;
import org.eclipse.xtext.common.types.JvmTypeReference;
import org.eclipse.xtext.generator.trace.LocationData;
import org.eclipse.xtext.util.ITextRegionWithLineInformation;
import org.eclipse.xtext.util.Strings;
import org.eclipse.xtext.xbase.XCatchClause;
import org.eclipse.xtext.xbase.XConstructorCall;
import org.eclipse.xtext.xbase.XExpression;
import org.eclipse.xtext.xbase.XForLoopExpression;
import org.eclipse.xtext.xbase.XListLiteral;
import org.eclipse.xtext.xbase.XStringLiteral;
import org.eclipse.xtext.xbase.XVariableDeclaration;
import org.eclipse.xtext.xbase.XbasePackage;
import org.eclipse.xtext.xbase.compiler.GeneratorConfigProvider;
import org.eclipse.xtext.xbase.compiler.JvmModelGenerator;
import org.eclipse.xtext.xbase.compiler.Later;
import org.eclipse.xtext.xbase.compiler.XbaseCompiler;
import org.eclipse.xtext.xbase.compiler.output.ITreeAppendable;
import org.eclipse.xtext.xbase.lib.Extension;
import org.eclipse.xtext.xbase.typesystem.references.LightweightTypeReference;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * @author Sven Efftinge - Initial contribution and API
 * @author Jan Koehnlein
 * @author Sebastian Zarnekow
 */
@NonNullByDefault
public class XtendCompiler extends XbaseCompiler {

	@Inject
	private RichStringProcessor richStringProcessor;

	@Inject	
	private Provider<DefaultIndentationHandler> indentationHandler;
	
	@Inject 
	private JvmModelGenerator jvmModelGenerator;
	
	@Inject 
	private GeneratorConfigProvider generatorConfigProvider;
	
	@Inject
	private IXtendJvmAssociations associations;
	
	@Override
	protected String getFavoriteVariableName(EObject ex) {
		if (ex instanceof RichStringForLoop)
			return "hasAnyElements";
		return super.getFavoriteVariableName(ex);
	}
	
	public class RichStringPrepareCompiler extends AbstractRichStringPartAcceptor.ForLoopOnce {

		private final LinkedList<ITreeAppendable> appendableStack;
		private final LinkedList<RichStringIf> ifStack;
		private final LinkedList<RichStringForLoop> forStack;
		private final String variableName;
		private ITreeAppendable appendable;
		private ITreeAppendable currentAppendable;

		public RichStringPrepareCompiler(ITreeAppendable appendable, String variableName, RichString richString) {
			this.ifStack = Lists.newLinkedList();
			this.forStack = Lists.newLinkedList();
			this.appendableStack = Lists.newLinkedList();
			this.appendable = appendable;
			this.variableName = variableName;
			List<XExpression> expressions = richString.getExpressions();
			if (!expressions.isEmpty() && expressions.get(0) instanceof RichStringLiteral)
				setCurrentAppendable((RichStringLiteral) expressions.get(0));
		}

		@Override
		public void acceptSemanticLineBreak(int charCount, RichStringLiteral origin, boolean controlStructureSeen) {
			setCurrentAppendable(origin);
			currentAppendable.newLine();
			currentAppendable.append(variableName);
			if (!controlStructureSeen) {
				currentAppendable.append(".newLine();");
			} else {
				currentAppendable.append(".newLineIfNotEmpty();");
			}
		}

		protected void setCurrentAppendable(@Nullable RichStringLiteral origin) {
			if (currentAppendable == null && origin != null) {
				ITextRegionWithLineInformation region = (ITextRegionWithLineInformation) getLocationInFileProvider().getSignificantTextRegion(origin, XbasePackage.Literals.XSTRING_LITERAL__VALUE, 0);
				currentAppendable = appendable.trace(new LocationData(region, null), true);
			}
		}

		@Override
		public void acceptTemplateLineBreak(int charCount, RichStringLiteral origin) {
			setCurrentAppendable(origin);
		}

		@Override
		public void acceptSemanticText(CharSequence text, @Nullable RichStringLiteral origin) {
			setCurrentAppendable(origin);
			if (text.length() == 0)
				return;
			currentAppendable.newLine();
			currentAppendable.append(variableName);
			currentAppendable.append(".append(\"");
			currentAppendable.append(Strings.convertToJavaString(text.toString(), false));
			currentAppendable.append("\");");
		}

		@Override
		public void acceptIfCondition(XExpression condition) {
			currentAppendable = null;
			ifStack.add((RichStringIf) condition.eContainer());
			appendable.newLine();
			pushAppendable(condition.eContainer());
			appendable.append("{").increaseIndentation();
			writeIf(condition);
		}

		protected void pushAppendable(EObject traceInfo) {
			appendableStack.add(appendable);
			appendable = appendable.trace(traceInfo);
		}
		
		protected void popAppendable() {
			appendable = appendableStack.removeLast();
		}

		@Override
		public void acceptElseIfCondition(XExpression condition) {
			currentAppendable = null;
			writeElse();
			writeIf(condition);
		}

		protected void writeIf(XExpression condition) {
			ITreeAppendable debugAppendable = appendable.trace(condition.eContainer(), true);
			internalToJavaStatement(condition, debugAppendable, true);
			debugAppendable.newLine();
			debugAppendable.append("if (");
			internalToJavaExpression(condition, debugAppendable);
			debugAppendable.append(") {").increaseIndentation();
		}

		protected void writeElse() {
			currentAppendable = null;
			appendable.decreaseIndentation();
			appendable.newLine();
			appendable.append("} else {");
			appendable.increaseIndentation();
		}

		@Override
		public void acceptElse() {
			currentAppendable = null;
			writeElse();
		}

		@Override
		public void acceptEndIf() {
			currentAppendable = null;
			RichStringIf richStringIf = ifStack.removeLast();
			for (int i = 0; i < richStringIf.getElseIfs().size() + 2; i++) {
				appendable.decreaseIndentation();
				appendable.newLine();
				appendable.append("}");
			}
			popAppendable();
		}

		@Override
		public void acceptForLoop(JvmFormalParameter parameter, @Nullable XExpression expression) {
			currentAppendable = null;
			super.acceptForLoop(parameter, expression);
			if (expression == null)
				throw new IllegalArgumentException("expression may not be null");
			RichStringForLoop forLoop = (RichStringForLoop) expression.eContainer();
			forStack.add(forLoop);
			appendable.newLine();
			pushAppendable(forLoop);
			appendable.append("{").increaseIndentation();
			
			ITreeAppendable debugAppendable = appendable.trace(forLoop, true);
			internalToJavaStatement(expression, debugAppendable, true);
			String variableName = null;
			if (forLoop.getBefore() != null || forLoop.getSeparator() != null || forLoop.getAfter() != null) {
				variableName = debugAppendable.declareSyntheticVariable(forLoop, "_hasElements");
				debugAppendable.newLine();
				debugAppendable.append("boolean ");
				debugAppendable.append(variableName);
				debugAppendable.append(" = false;");
			}
			debugAppendable.newLine();
			debugAppendable.append("for(final ");
			// TODO tracing if parameter was explicitly declared
			LightweightTypeReference paramType = getLightweightType(parameter);
			if (paramType != null) {
				debugAppendable.append(paramType);
			} else {
				debugAppendable.append("Object");
			}
			debugAppendable.append(" ");
			String loopParam = debugAppendable.declareVariable(parameter, parameter.getName());
			debugAppendable.append(loopParam);
			debugAppendable.append(" : ");
			internalToJavaExpression(expression, debugAppendable);
			debugAppendable.append(") {").increaseIndentation();
		}
		
		@Override
		public boolean forLoopHasNext(@Nullable XExpression before, @Nullable XExpression separator, CharSequence indentation) {
			currentAppendable = null;
			if (!super.forLoopHasNext(before, separator, indentation))
				return false;
			RichStringForLoop forLoop = forStack.getLast();
			if (appendable.hasName(forLoop)) {
				String varName = getVarName(forLoop, appendable);
				appendable.newLine();
				appendable.append("if (!");
				appendable.append(varName);
				appendable.append(") {");
				appendable.increaseIndentation();
				appendable.newLine();
				appendable.append(varName);
				appendable.append(" = true;");
				if (before != null) {
					writeExpression(before, indentation, false);
				}
				appendable.decreaseIndentation();
				appendable.newLine();
				appendable.append("}");
				if (separator != null) {
					appendable.append(" else {");
					appendable.increaseIndentation();
					writeExpression(separator, indentation, true);
					appendable.decreaseIndentation();
					appendable.newLine();
					appendable.append("}");
				}
			}
			return true;
		}
		
		@Override
		public void acceptEndFor(@Nullable XExpression after, CharSequence indentation) {
			currentAppendable = null;
			super.acceptEndFor(after, indentation);
			appendable.decreaseIndentation();
			appendable.newLine();
			appendable.append("}");
			
			RichStringForLoop forLoop = forStack.removeLast();
			if (after != null) {
				String varName = getVarName(forLoop, appendable);
				appendable.newLine();
				appendable.append("if (");
				appendable.append(varName);
				appendable.append(") {");
				appendable.increaseIndentation();
				writeExpression(after, indentation, false);
				appendable.decreaseIndentation();
				appendable.newLine();
				appendable.append("}");
			}
			
			appendable.decreaseIndentation();
			appendable.newLine();
			appendable.append("}");
			popAppendable();
		}

		@Override
		public void acceptExpression(XExpression expression, CharSequence indentation) {
			currentAppendable = null;
			writeExpression(expression, indentation, false);
		}

		protected void writeExpression(XExpression expression, CharSequence indentation, boolean immediate) {
			boolean referenced = !isPrimitiveVoid(expression);
			internalToJavaStatement(expression, appendable, referenced);
			if (referenced) {
				ITreeAppendable tracingAppendable = appendable.trace(expression, true);
				tracingAppendable.newLine();
				tracingAppendable.append(variableName);
				if (immediate)
					tracingAppendable.append(".appendImmediate(");
				else
					tracingAppendable.append(".append(");
				internalToJavaExpression(expression, tracingAppendable);
				tracingAppendable.append(", \"");
				tracingAppendable.append(Strings.convertToJavaString(indentation.toString(), false));
				tracingAppendable.append("\");");
			}
		}

	}
	
	@Override
	protected XExpression normalizeBlockExpression(XExpression expr) {
		if (expr instanceof RichString)
			return expr;
		return super.normalizeBlockExpression(expr);
	}
	
	@Override
	public void doInternalToJavaStatement(XExpression obj, ITreeAppendable appendable, boolean isReferenced) {
		if(obj instanceof AnonymousClassConstructorCall) 
			_toJavaStatement((AnonymousClassConstructorCall)obj, appendable, isReferenced);
		else if (obj instanceof RichString)
			_toJavaStatement((RichString)obj, appendable, isReferenced);
		else
			super.doInternalToJavaStatement(obj, appendable, isReferenced);
	}

	public void _toJavaStatement(RichString richString, ITreeAppendable b, boolean isReferenced) {
		LightweightTypeReference actualType = getLightweightType(richString);
		b = b.trace(richString);
		if (actualType.isType(StringConcatenationClient.class)) {
			String resultVariableName = b.declareSyntheticVariable(richString, "_client");
			b.newLine();
			b.append(actualType);
			b.append(" ");
			b.append(resultVariableName);
			b.append(" = new ");
			b.append(actualType);
			b.append("() {");
			b.openScope();
			reassignThisInClosure(b, actualType.getType());
			b.increaseIndentation().newLine();
			b.append("@");
			b.append(Override.class);
			b.newLine().append("protected void appendTo(");
			b.append(StringConcatenationClient.TargetStringConcatenation.class);
			String variableName = b.declareSyntheticVariable(richString, "_builder");
			b.append(" ").append(variableName).append(") {");
			b.increaseIndentation();
			RichStringPrepareCompiler compiler = new RichStringPrepareCompiler(b, variableName, richString);
			richStringProcessor.process(richString, compiler, indentationHandler.get());
			b.closeScope();
			b.decreaseIndentation().newLine().append("}").decreaseIndentation().newLine().append("};");
		} else {
			// declare variable
			String variableName = b.declareSyntheticVariable(richString, "_builder");
			b.newLine();
			b.append(StringConcatenation.class);
			b.append(" ");
			b.append(variableName);
			b.append(" = new ");
			b.append(StringConcatenation.class);
			b.append("();");
			RichStringPrepareCompiler compiler = new RichStringPrepareCompiler(b, variableName, richString);
			richStringProcessor.process(richString, compiler, indentationHandler.get());
		}
	}

	@Override
	public void internalToConvertedExpression(XExpression obj, ITreeAppendable appendable) {
		if (obj instanceof RichString)
			_toJavaExpression((RichString) obj, appendable);
		else
			super.internalToConvertedExpression(obj, appendable);
	}
	
	public void _toJavaExpression(RichString richString, ITreeAppendable b) {
		b.append(getVarName(richString, b));
		if(getLightweightType(richString).isType(String.class))
			b.append(".toString()");
	}
	
	@Override
	protected void appendCatchClauseParameter(XCatchClause catchClause, JvmTypeReference parameterType,
			String parameterName, ITreeAppendable appendable) {
		appendExtensionAnnotation(catchClause.getDeclaredParam(), catchClause, appendable, false);
		super.appendCatchClauseParameter(catchClause, parameterType, parameterName, appendable);
	}

	protected void appendExtensionAnnotation(JvmFormalParameter parameter, EObject context,
			ITreeAppendable appendable, boolean newLine) {
		if (parameter instanceof XtendFormalParameter) {
			XtendFormalParameter castedParameter = (XtendFormalParameter) parameter;
			if (castedParameter.isExtension()) {
				appendExtensionAnnotation(context, appendable, newLine);
			}
		}
	}

	protected void appendExtensionAnnotation(EObject context, ITreeAppendable appendable, boolean newLine) {
		JvmType extension = findKnownTopLevelType(Extension.class, context);
		if (extension != null) {
			appendable.append("@");
			appendable.append(extension);
			if (!newLine)
				appendable.append(" ");
			else
				appendable.newLine();
		}
	}
	
	@Override
	protected LightweightTypeReference appendVariableTypeAndName(XVariableDeclaration varDeclaration, ITreeAppendable appendable) {
		if (varDeclaration instanceof XtendVariableDeclaration && ((XtendVariableDeclaration) varDeclaration).isExtension())
			appendExtensionAnnotation(varDeclaration, appendable, true);
		return super.appendVariableTypeAndName(varDeclaration, appendable);
	}
	
	@Override
	protected void appendForLoopParameter(XForLoopExpression expr, ITreeAppendable appendable) {
		appendExtensionAnnotation(expr.getDeclaredParam(), expr, appendable, false);
		super.appendForLoopParameter(expr, appendable);
	}
	
	@Override
	protected void appendClosureParameter(JvmFormalParameter closureParam, LightweightTypeReference parameterType,
			ITreeAppendable appendable) {
		appendExtensionAnnotation(closureParam, closureParam, appendable, false);
		super.appendClosureParameter(closureParam, parameterType, appendable);
	}
	
	@Override
	protected boolean canUseArrayInitializer(XListLiteral literal, ITreeAppendable appendable) {
		EStructuralFeature feature = literal.eContainingFeature();
		if (feature == XtendPackage.Literals.XTEND_FIELD__INITIAL_VALUE) {
			return canUseArrayInitializerImpl(literal, appendable);
		}
		return super.canUseArrayInitializer(literal, appendable);
	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * Specialized since unicode escapes are handled in the {@link UnicodeAwarePostProcessor}.
	 */
	@Override
	public void _toJavaExpression(XStringLiteral expr, ITreeAppendable b) {
		toJavaExpression(expr, b, false);
	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * Specialized since unicode escapes are handled in the {@link UnicodeAwarePostProcessor}.
	 */
	@Override
	public void _toJavaStatement(final XStringLiteral expr, ITreeAppendable b, boolean isReferenced) {
		toJavaStatement(expr, b, isReferenced, false);
	}
	
	protected void _toJavaStatement(final AnonymousClassConstructorCall expr, ITreeAppendable b, final boolean isReferenced) {
		for (XExpression arg : expr.getArguments()) {
			prepareExpression(arg, b);
		}
		XtendClass anonymousXtendClass = expr.getAnonymousClass();
		JvmGenericType inferredLocalClass = associations.getInferredType(anonymousXtendClass);
		if(!inferredLocalClass.isAnonymous()) {
			jvmModelGenerator.generateBody(inferredLocalClass, b, generatorConfigProvider.get(expr));
		} 
		if (!isReferenced) {
			b.newLine();
			constructorCallToJavaExpression(expr, b);
			b.append(";");
		} else if (isVariableDeclarationRequired(expr, b)) {
			Later later = new Later() {
				public void exec(ITreeAppendable appendable) {
					constructorCallToJavaExpression(expr, appendable);
				}
			};
			declareFreshLocalVariable(expr, b, later);
		}
	}
	
	@Override
	protected boolean internalCanCompileToJavaExpression(XExpression expression, ITreeAppendable appendable) {
		if(expression instanceof AnonymousClassConstructorCall) {
			XtendClass anonymousXtendClass = ((AnonymousClassConstructorCall) expression).getAnonymousClass();
			JvmGenericType inferredLocalClass = associations.getInferredType(anonymousXtendClass);
			return inferredLocalClass.isAnonymous();
		} else return super.internalCanCompileToJavaExpression(expression, appendable);
	}
	
	@Override
	protected void appendConstructedTypeName(XConstructorCall constructorCall, ITreeAppendable typeAppendable) {
		if(constructorCall instanceof AnonymousClassConstructorCall) {
			XtendClass anonymousXtendClass = ((AnonymousClassConstructorCall) constructorCall).getAnonymousClass();
			JvmGenericType inferredAnonymousClass = associations.getInferredType(anonymousXtendClass);
			if(inferredAnonymousClass.isAnonymous()) {
				typeAppendable.append(inferredAnonymousClass.getSuperTypes().get(0).getType());
				return;
			}
		}
		super.appendConstructedTypeName(constructorCall, typeAppendable);
	}
	
	@Override
	protected void constructorCallToJavaExpression(XConstructorCall constructorCall, ITreeAppendable b) {
		super.constructorCallToJavaExpression(constructorCall, b);
		JvmDeclaredType declaringType = constructorCall.getConstructor().getDeclaringType();
		if(declaringType instanceof JvmGenericType && ((JvmGenericType) declaringType).isAnonymous()) {
			ITreeAppendable appendable = b.trace(((AnonymousClassConstructorCall) constructorCall).getAnonymousClass(), true);
			appendable.openScope();
			appendable.declareVariable(declaringType, "this");
			appendable.declareVariable(declaringType.getSuperTypes().get(0).getType(), "super");
			jvmModelGenerator.generateBody(declaringType, appendable, generatorConfigProvider.get(constructorCall));
			appendable.closeScope();
		}
	}
}
