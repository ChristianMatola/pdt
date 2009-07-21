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
package org.eclipse.php.internal.ui.editor.contentassist;

import org.eclipse.core.runtime.Platform;
import org.eclipse.dltk.core.*;
import org.eclipse.dltk.ui.text.ScriptTextTools;
import org.eclipse.dltk.ui.text.completion.ScriptOverrideCompletionProposal;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ContextInformation;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension4;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.php.internal.core.PHPCoreConstants;
import org.eclipse.php.internal.core.PHPCorePlugin;
import org.eclipse.php.internal.core.compiler.ast.nodes.NamespaceReference;
import org.eclipse.php.internal.ui.PHPUiPlugin;

public class PHPOverrideCompletionProposal extends
		ScriptOverrideCompletionProposal implements
		ICompletionProposalExtension4 {

	public PHPOverrideCompletionProposal(IScriptProject jproject,
			ISourceModule cu, String methodName, String[] paramTypes,
			int start, int length, String displayName, String completionProposal) {
		super(jproject, cu, methodName, paramTypes, start, length, displayName,
				completionProposal);
	}

	public void apply(IDocument document, char trigger, int offset) {
		UseStatementInjector injector = new UseStatementInjector(this);
		offset = injector.inject(document, getTextViewer(), offset);

		super.apply(document, trigger, offset);

		calculateCursorPosition(document, offset);
	}

	public boolean isAutoInsertable() {
		return Platform.getPreferencesService().getBoolean(PHPCorePlugin.ID,
				PHPCoreConstants.CODEASSIST_AUTOINSERT, false, null);
	}

	protected boolean insertCompletion() {
		return Platform.getPreferencesService().getBoolean(PHPCorePlugin.ID,
				PHPCoreConstants.CODEASSIST_INSERT_COMPLETION, true, null);
	}

	protected void calculateCursorPosition(IDocument document, int offset) {
		try {
			while (Character.isJavaIdentifierPart(document.getChar(offset))
					|| document.getChar(offset) == NamespaceReference.NAMESPACE_SEPARATOR) {
				++offset;
			}
			if (document.getChar(offset) == '(') {
				boolean hasArguments = false;
				IModelElement modelElement = getModelElement();
				if (modelElement.getElementType() == IModelElement.METHOD) {
					IMethod method = (IMethod) modelElement;
					try {
						String[] parameters = method.getParameters();
						if (parameters != null && parameters.length > 0) {
							hasArguments = true;
						}
					} catch (ModelException e) {
					}
				}
				if (!hasArguments) {
					setCursorPosition(offset - getReplacementOffset() + 2);
				} else {
					setCursorPosition(offset - getReplacementOffset() + 1);
				}
			}
		} catch (BadLocationException e) {
		}
	}

	public IContextInformation getContextInformation() {
		String displayString = getDisplayString();
		String infoDisplayString = displayString;

		int i = infoDisplayString.indexOf('(');
		if (i != -1) {
			infoDisplayString = infoDisplayString.substring(i + 1);
		}
		i = infoDisplayString.indexOf(')');
		if (i != -1) {
			infoDisplayString = infoDisplayString.substring(0, i);
		}
		if (infoDisplayString.length() == 0) {
			return null;
		}
		return new ContextInformation(displayString, infoDisplayString);
	}

	protected boolean isCamelCaseMatching() {
		return true;
	}

	protected ScriptTextTools getTextTools() {
		return PHPUiPlugin.getDefault().getTextTools();
	}
}
