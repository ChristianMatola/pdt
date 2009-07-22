/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Zend Technologies
 *******************************************************************************/
package org.eclipse.php.internal.core.codeassist.strategies;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.dltk.core.CompletionRequestor;
import org.eclipse.dltk.core.IField;
import org.eclipse.dltk.core.IType;
import org.eclipse.dltk.core.ITypeHierarchy;
import org.eclipse.dltk.internal.core.SourceRange;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.php.internal.core.PHPCorePlugin;
import org.eclipse.php.internal.core.codeassist.CodeAssistUtils;
import org.eclipse.php.internal.core.codeassist.ICompletionReporter;
import org.eclipse.php.internal.core.codeassist.contexts.ClassMemberContext;
import org.eclipse.php.internal.core.codeassist.contexts.ICompletionContext;
import org.eclipse.php.internal.core.codeassist.contexts.ClassMemberContext.Trigger;
import org.eclipse.php.internal.core.typeinference.PHPModelUtils;

/**
 * This strategy completes class constants and variables.
 * 
 * @author michael
 */
public class ClassFieldsStrategy extends ClassMembersStrategy {

	public ClassFieldsStrategy(ICompletionContext context,
			IElementFilter elementFilter) {
		super(context, elementFilter);
	}

	public ClassFieldsStrategy(ICompletionContext context) {
		super(context);
	}

	public void apply(ICompletionReporter reporter) throws BadLocationException {
		ICompletionContext context = getContext();
		if (!(context instanceof ClassMemberContext)) {
			return;
		}

		ClassMemberContext concreteContext = (ClassMemberContext) context;
		CompletionRequestor requestor = concreteContext
				.getCompletionRequestor();

		String prefix = concreteContext.getPrefix();
		SourceRange replaceRange = getReplacementRange(concreteContext);

		Set<IField> result = new TreeSet<IField>(
				new CodeAssistUtils.AlphabeticComparator());
		for (IType type : concreteContext.getLhsTypes()) {
			try {
				ITypeHierarchy hierarchy = getCompanion()
						.getSuperTypeHierarchy(type, null);

				IField[] fields = PHPModelUtils.getTypeHierarchyField(type,
						hierarchy, prefix,
						requestor.isContextInformationMode(), null);

				for (IField field : removeOverriddenElements(Arrays
						.asList(fields))) {
					if (!isFiltered(field, concreteContext)) {
						result.add(field);
					}
				}
			} catch (CoreException e) {
				PHPCorePlugin.log(e);
			}
		}
		for (IField field : result) {
			reporter.reportField(field, getSuffix(), replaceRange,
					concreteContext.getTriggerType() == Trigger.OBJECT);
		}
	}

	protected boolean showNonStaticMembers(ClassMemberContext context) {
		return super.showNonStaticMembers(context) && !isParentCall(context);
	}

	public String getSuffix() {
		return "";
	}
}