/*******************************************************************************
 * Copyright (c) 2012 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Markus Schorn - initial API and implementation
 *     Sergey Prigogin (Google)
 *     Nathan Ridge
 *******************************************************************************/
package org.eclipse.cdt.internal.core.dom.parser.cpp.semantics;

import static org.eclipse.cdt.core.dom.ast.IASTExpression.ValueCategory.PRVALUE;

import java.util.Arrays;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTExpression.ValueCategory;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.IValue;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassSpecialization;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassTemplate;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPFunction;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPTemplateArgument;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPTemplateParameterMap;
import org.eclipse.cdt.internal.core.dom.parser.ISerializableEvaluation;
import org.eclipse.cdt.internal.core.dom.parser.ITypeMarshalBuffer;
import org.eclipse.cdt.internal.core.dom.parser.Value;
import org.eclipse.cdt.internal.core.dom.parser.cpp.ICPPEvaluation;
import org.eclipse.cdt.internal.core.dom.parser.cpp.ICPPUnknownBinding;
import org.eclipse.core.runtime.CoreException;

/**
 * Performs evaluation of an expression.
 */
public class EvalFunctionSet extends CPPDependentEvaluation {
	private final CPPFunctionSet fFunctionSet;
	private final boolean fAddressOf;
	
	// Where an EvalFunctionSet is created for an expression of the form 'obj.member_function', 
	// the type of 'obj' (needed for correct overload resolution of 'member_function' later). 
	// Otherwise null.
	private final IType fImpliedObjectType;

	public EvalFunctionSet(CPPFunctionSet set, boolean addressOf, IType impliedObjectType, IASTNode pointOfDefinition) {
		this(set, addressOf, impliedObjectType, findEnclosingTemplate(pointOfDefinition));
	}
	public EvalFunctionSet(CPPFunctionSet set, boolean addressOf, IType impliedObjectType, IBinding templateDefinition) {
		super(templateDefinition);
		fFunctionSet= set;
		fAddressOf= addressOf;
		fImpliedObjectType= impliedObjectType;
	}

	public CPPFunctionSet getFunctionSet() {
		return fFunctionSet;
	}

	public boolean isAddressOf() {
		return fAddressOf;
	}
	
	public IType getImpliedObjectType() {
		return fImpliedObjectType;
	}

	@Override
	public boolean isInitializerList() {
		return false;
	}

	@Override
	public boolean isFunctionSet() {
		return true;
	}

	@Override
	public boolean isTypeDependent() {
		final ICPPTemplateArgument[] args = fFunctionSet.getTemplateArguments();
		if (args != null) {
			for (ICPPTemplateArgument arg : args) {
				if (CPPTemplates.isDependentArgument(arg))
					return true;
			}
		}
		for (ICPPFunction f : fFunctionSet.getBindings()) {
			if (f instanceof ICPPUnknownBinding)
				return true;
		}
		return false;
	}

	@Override
	public boolean isValueDependent() {
		return false;
	}

	@Override
	public IType getTypeOrFunctionSet(IASTNode point) {
		return new FunctionSetType(fFunctionSet, fAddressOf);
	}

	@Override
	public IValue getValue(IASTNode point) {
		return Value.UNKNOWN;
	}

	@Override
	public ValueCategory getValueCategory(IASTNode point) {
		return PRVALUE;
	}

	@Override
	public void marshal(ITypeMarshalBuffer buffer, boolean includeValue) throws CoreException {
		final ICPPFunction[] bindings = fFunctionSet.getBindings();
		final ICPPTemplateArgument[] args = fFunctionSet.getTemplateArguments();
		short firstBytes = ITypeMarshalBuffer.EVAL_FUNCTION_SET;
		if (fAddressOf)
			firstBytes |= ITypeMarshalBuffer.FLAG1;
		if (args != null)
			firstBytes |= ITypeMarshalBuffer.FLAG2;

		buffer.putShort(firstBytes);
		buffer.putInt(bindings.length);
		for (ICPPFunction binding : bindings) {
			buffer.marshalBinding(binding);
		}
		if (args != null) {
			buffer.putInt(args.length);
			for (ICPPTemplateArgument arg : args) {
				buffer.marshalTemplateArgument(arg);
			}
		}
		buffer.marshalType(fImpliedObjectType);
		marshalTemplateDefinition(buffer);
	}

	public static ISerializableEvaluation unmarshal(short firstBytes, ITypeMarshalBuffer buffer) throws CoreException {
		final boolean addressOf= (firstBytes & ITypeMarshalBuffer.FLAG1) != 0;
		int bindingCount= buffer.getInt();
		ICPPFunction[] bindings= new ICPPFunction[bindingCount];
		for (int i = 0; i < bindings.length; i++) {
			bindings[i]= (ICPPFunction) buffer.unmarshalBinding();
		}
		ICPPTemplateArgument[] args= null;
		if ((firstBytes & ITypeMarshalBuffer.FLAG2) != 0) {
			int len= buffer.getInt();
			args = new ICPPTemplateArgument[len];
			for (int i = 0; i < args.length; i++) {
				args[i]= buffer.unmarshalTemplateArgument();
			}
		}
		IType impliedObjectType= buffer.unmarshalType();
		IBinding templateDefinition= buffer.unmarshalBinding();
		return new EvalFunctionSet(new CPPFunctionSet(bindings, args, null), addressOf, impliedObjectType, templateDefinition);
	}

	@Override
	public ICPPEvaluation instantiate(ICPPTemplateParameterMap tpMap, int packOffset,
			ICPPClassSpecialization within, int maxdepth, IASTNode point) {
		ICPPTemplateArgument[] originalArguments = fFunctionSet.getTemplateArguments();
		ICPPTemplateArgument[] arguments = originalArguments;
		if (originalArguments != null)
			arguments = instantiateArguments(originalArguments, tpMap, packOffset, within, point);

		IBinding originalOwner = fFunctionSet.getOwner();
		IBinding owner = originalOwner;
		if (owner instanceof ICPPUnknownBinding) {
			owner = resolveUnknown((ICPPUnknownBinding) owner, tpMap, packOffset, within, point);
		} else if (owner instanceof ICPPClassTemplate) {
			owner = resolveUnknown(CPPTemplates.createDeferredInstance((ICPPClassTemplate) owner),
					tpMap, packOffset, within, point);
		} else if (owner instanceof IType) {
			IType type = CPPTemplates.instantiateType((IType) owner, tpMap, packOffset, within, point);
			if (type instanceof IBinding)
				owner = (IBinding) type;
		}
		ICPPFunction[] originalFunctions = fFunctionSet.getBindings();
		ICPPFunction[] functions = originalFunctions;
		if (owner instanceof ICPPClassSpecialization && owner != originalOwner) {
			functions = new ICPPFunction[originalFunctions.length];
			for (int i = 0; i < originalFunctions.length; i++) {
				functions[i] = (ICPPFunction) CPPTemplates.createSpecialization((ICPPClassSpecialization) owner,
						originalFunctions[i], point);
			}
		}
		// No need to instantiate the implied object type. An EvalFunctioNSet should only be created
		// with an implied object type when that type is not dependent.
		if (Arrays.equals(arguments, originalArguments) && functions == originalFunctions)
			return this;
		return new EvalFunctionSet(new CPPFunctionSet(functions, arguments, null), fAddressOf, fImpliedObjectType, getTemplateDefinition());
	}

	@Override
	public ICPPEvaluation computeForFunctionCall(CPPFunctionParameterMap parameterMap,
			int maxdepth, IASTNode point) {
		return this;
	}

	/**
	 * Attempts to resolve the function using the parameters of a function call.
	 *
	 * @param args the arguments of a function call
	 * @param point the name lookup context
	 * @return the resolved or the original evaluation depending on whether function resolution
	 *     succeeded or not
	 */
	public ICPPEvaluation resolveFunction(ICPPEvaluation[] args, IASTNode point) {
		ICPPFunction[] functions = fFunctionSet.getBindings();
		LookupData data = new LookupData(functions[0].getNameCharArray(),
				fFunctionSet.getTemplateArguments(), point);
		data.setFunctionArguments(false, args);
		if (fImpliedObjectType != null)
			data.setImpliedObjectType(fImpliedObjectType);
		try {
			IBinding binding = CPPSemantics.resolveFunction(data, functions, true);
			if (binding instanceof ICPPFunction && !(binding instanceof ICPPUnknownBinding))
				return new EvalBinding(binding, null, getTemplateDefinition());
		} catch (DOMException e) {
			CCorePlugin.log(e);
		}
		return this;
	}

	@Override
	public int determinePackSize(ICPPTemplateParameterMap tpMap) {
		int r = CPPTemplates.PACK_SIZE_NOT_FOUND;
		ICPPTemplateArgument[] templateArguments = fFunctionSet.getTemplateArguments();
		for (ICPPTemplateArgument arg : templateArguments) {
			r = CPPTemplates.combinePackSize(r, CPPTemplates.determinePackSize(arg, tpMap));
		}
		return r;
	}

	@Override
	public boolean referencesTemplateParameter() {
		return false;
	}
}
