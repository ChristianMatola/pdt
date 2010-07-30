/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *		Andrew McCullough - initial API and implementation
 *		IBM Corporation  - general improvement and bug fixes, partial reimplementation
 *******************************************************************************/
package org.eclipse.php.internal.ui.editor.contentassist;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.Platform;
import org.eclipse.dltk.core.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.*;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.link.*;
import org.eclipse.php.internal.core.PHPCoreConstants;
import org.eclipse.php.internal.core.PHPCorePlugin;
import org.eclipse.php.internal.core.typeinference.FakeConstructor;
import org.eclipse.php.internal.ui.PHPUiPlugin;
import org.eclipse.php.internal.ui.text.template.contentassist.PositionBasedCompletionProposal;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.texteditor.link.EditorLinkedModeUI;

/**
 * This is a
 * {@link org.eclipse.jdt.internal.ui.text.java.JavaCompletionProposal} which
 * includes templates that represent the best guess completion for each
 * parameter of a method.
 */
public final class ParameterGuessingProposal extends
		PHPOverrideCompletionProposal {
	private static final char[] NO_TRIGGERS = new char[0];
	protected static final String LPAREN = "("; //$NON-NLS-1$
	protected static final String RPAREN = ")"; //$NON-NLS-1$
	protected static final String COMMA = ", "; //$NON-NLS-1$
	protected static final String SPACE = " "; //$NON-NLS-1$
	private CompletionProposal fProposal;
	private IMethod method;
	private final boolean fFillBestGuess;
	private boolean fReplacementStringComputed = false;

	public ParameterGuessingProposal(CompletionProposal proposal,
			IScriptProject jproject, ISourceModule cu, String methodName,
			String[] paramTypes, int start, int length, String displayName,
			String completionProposal, boolean fillBestGuess) {
		super(jproject, cu, methodName, paramTypes, start, length, displayName,
				completionProposal);
		this.fProposal = proposal;
		method = (IMethod) fProposal.getModelElement();
		this.fFillBestGuess = fillBestGuess;
	}

	/**
	 * if modelElement is an instance of FakeConstructor, we need to get the
	 * real constructor
	 * 
	 * @param modelElement
	 * @return
	 */
	private IMethod getProperMethod(IMethod modelElement) {
		if (modelElement instanceof FakeConstructor) {
			FakeConstructor fc = (FakeConstructor) modelElement;
			IType type = modelElement.getDeclaringType();
			IMethod[] ctors = FakeConstructor.getConstructors(type, fc
					.isEnclosingClass());
			// here we must make sure ctors[1] != null,
			// it means there is an available FakeConstructor for ctors[0]
			if (ctors != null && ctors.length == 2 && ctors[0] != null
					&& ctors[1] != null) {
				return ctors[0];
			}
		}

		return modelElement;
	}

	private ICompletionProposal[][] fChoices; // initialized by
	// guessParameters()
	private Position[] fPositions; // initialized by guessParameters()

	private IRegion fSelectedRegion; // initialized by apply()
	private IPositionUpdater fUpdater;

	/*
	 * @see ICompletionProposalExtension#apply(IDocument, char)
	 */
	public void apply(IDocument document, char trigger, int offset) {
		try {
			super.apply(document, trigger, offset);

			int baseOffset = getReplacementOffset();
			String replacement = getReplacementString();

			if (fPositions != null && getTextViewer() != null) {

				LinkedModeModel model = new LinkedModeModel();

				for (int i = 0; i < fPositions.length; i++) {
					LinkedPositionGroup group = new LinkedPositionGroup();
					int positionOffset = fPositions[i].getOffset();
					int positionLength = fPositions[i].getLength();

					if (fChoices[i].length < 2) {
						group.addPosition(new LinkedPosition(document,
								positionOffset, positionLength,
								LinkedPositionGroup.NO_STOP));
					} else {
						ensurePositionCategoryInstalled(document, model);
						document.addPosition(getCategory(), fPositions[i]);
						group.addPosition(new ProposalPosition(document,
								positionOffset, positionLength,
								LinkedPositionGroup.NO_STOP, fChoices[i]));
					}
					model.addGroup(group);
				}

				model.forceInstall();

				LinkedModeUI ui = new EditorLinkedModeUI(model, getTextViewer());
				ui.setExitPosition(getTextViewer(), baseOffset
						+ replacement.length(), 0, Integer.MAX_VALUE);
				ui.setExitPolicy(new ExitPolicy(')', document));
				ui.setCyclingMode(LinkedModeUI.CYCLE_WHEN_NO_PARENT);
				ui.setDoContextInfo(true);
				ui.enter();
				fSelectedRegion = ui.getSelectedRegion();

			} else {
				fSelectedRegion = new Region(baseOffset + replacement.length(),
						0);
			}

		} catch (BadLocationException e) {
			ensurePositionCategoryRemoved(document);
			PHPUiPlugin.log(e);
			openErrorDialog(e);
		} catch (BadPositionCategoryException e) {
			ensurePositionCategoryRemoved(document);
			PHPUiPlugin.log(e);
			openErrorDialog(e);
		}
	}

	/*
	 * @seeorg.eclipse.jdt.internal.ui.text.java.JavaMethodCompletionProposal#
	 * needsLinkedMode()
	 */
	protected boolean needsLinkedMode() {
		return false; // we handle it ourselves
	}

	public String getReplacementString() {
		boolean fileArgumentNames = Platform.getPreferencesService()
				.getBoolean(PHPCorePlugin.ID,
						PHPCoreConstants.CODEASSIST_FILL_ARGUMENT_NAMES, true,
						null);
		if (fileArgumentNames && !fReplacementStringComputed)
			setReplacementString(computeReplacementString());
		return super.getReplacementString();
	}

	private String computeReplacementString() {
		fReplacementStringComputed = true;
		try {
			// we should get the real constructor here
			method = getProperMethod(method);
			if (hasParameters()) {
				return computeGuessingCompletion();
			}
		} catch (ModelException e) {
			e.printStackTrace();
		}
		return super.getReplacementString();
	}

	protected boolean isValidPrefix(String prefix) {
		return isPrefix(prefix, super.getReplacementString());
	}

	private boolean hasParameters() throws ModelException {
		return method.getParameters() != null
				&& hasNondefaultValues(method.getParameters());
	}

	private boolean hasNondefaultValues(IParameter[] parameters) {
		for (int i = 0; i < parameters.length; i++) {
			IParameter parameter = parameters[i];
			if (parameter.getDefaultValue() == null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates the completion string. Offsets and Lengths are set to the offsets
	 * and lengths of the parameters.
	 * 
	 * @return the completion string
	 * @throws ModelException
	 *             if parameter guessing failed
	 */
	private String computeGuessingCompletion() throws ModelException {

		StringBuffer buffer = new StringBuffer();
		appendMethodNameReplacement(buffer);

		setCursorPosition(buffer.length());
		// show method parameter names:
		IParameter[] parameters = method.getParameters();
		List<String> paramList = new ArrayList<String>();
		if (parameters != null) {
			for (int i = 0; i < parameters.length; i++) {
				IParameter parameter = parameters[i];
				if (parameter.getDefaultValue() == null) {
					paramList.add(parameter.getName());
				}
			}
		}
		char[][] parameterNames = new char[paramList.size()][];
		for (int i = 0; i < paramList.size(); ++i) {
			parameterNames[i] = paramList.get(i).toCharArray();
		}

		fChoices = guessParameters(parameterNames);
		int count = fChoices.length;
		int replacementOffset = getReplacementOffset();

		for (int i = 0; i < count; i++) {
			if (i != 0) {
				buffer.append(COMMA);
			}

			ICompletionProposal proposal = fChoices[i][0];
			String argument = proposal.getDisplayString();

			Position position = fPositions[i];
			position.setOffset(replacementOffset + buffer.length());
			position.setLength(argument.length());

			buffer.append(argument);
		}

		buffer.append(RPAREN);

		return buffer.toString();
	}

	/**
	 * Appends everything up to the method name including the opening
	 * parenthesis.
	 * <p>
	 * In case of {@link CompletionProposal#METHOD_REF_WITH_CASTED_RECEIVER} it
	 * add cast.
	 * </p>
	 * 
	 * @param buffer
	 *            the string buffer
	 * @since 3.4
	 */
	protected void appendMethodNameReplacement(StringBuffer buffer) {
		buffer.append(fProposal.getName());
		buffer.append(LPAREN);
	}

	private ICompletionProposal[][] guessParameters(char[][] parameterNames)
			throws ModelException {
		// find matches in reverse order. Do this because people tend to declare
		// the variable meant for the last
		// parameter last. That is, local variables for the last parameter in
		// the method completion are more
		// likely to be closer to the point of code completion. As an example
		// consider a "delegation" completion:
		//
		// public void myMethod(int param1, int param2, int param3) {
		// someOtherObject.yourMethod(param1, param2, param3);
		// }
		//
		// The other consideration is giving preference to variables that have
		// not previously been used in this
		// code completion (which avoids
		// "someOtherObject.yourMethod(param1, param1, param1)";

		int count = parameterNames.length;
		fPositions = new Position[count];
		fChoices = new ICompletionProposal[count][];

		IParameter[] parameters = method.getParameters();

		for (int i = count - 1; i >= 0; i--) {
			String paramName = new String(parameterNames[i]);
			Position position = new Position(0, 0);

			ICompletionProposal[] argumentProposals = parameterProposals(
					parameters[i].getDefaultValue(), paramName, position,
					fFillBestGuess);

			fPositions[i] = position;
			fChoices[i] = argumentProposals;
		}

		return fChoices;
	}

	/*
	 * @see ICompletionProposal#getSelection(IDocument)
	 */
	public Point getSelection(IDocument document) {
		if (fSelectedRegion == null)
			return new Point(getReplacementOffset(), 0);

		return new Point(fSelectedRegion.getOffset(), fSelectedRegion
				.getLength());
	}

	private void openErrorDialog(Exception e) {
		Shell shell = getTextViewer().getTextWidget().getShell();
		MessageDialog.openError(shell, "Error guessing parameters", e
				.getMessage());
	}

	private void ensurePositionCategoryInstalled(final IDocument document,
			LinkedModeModel model) {
		if (!document.containsPositionCategory(getCategory())) {
			document.addPositionCategory(getCategory());
			fUpdater = new InclusivePositionUpdater(getCategory());
			document.addPositionUpdater(fUpdater);

			model.addLinkingListener(new ILinkedModeListener() {

				/*
				 * @see
				 * org.eclipse.jface.text.link.ILinkedModeListener#left(org.
				 * eclipse.jface.text.link.LinkedModeModel, int)
				 */
				public void left(LinkedModeModel environment, int flags) {
					ensurePositionCategoryRemoved(document);
				}

				public void suspend(LinkedModeModel environment) {
				}

				public void resume(LinkedModeModel environment, int flags) {
				}
			});
		}
	}

	private void ensurePositionCategoryRemoved(IDocument document) {
		if (document.containsPositionCategory(getCategory())) {
			try {
				document.removePositionCategory(getCategory());
			} catch (BadPositionCategoryException e) {
				// ignore
			}
			document.removePositionUpdater(fUpdater);
		}
	}

	private String getCategory() {
		return "ParameterGuessingProposal_" + toString(); //$NON-NLS-1$
	}

	/**
	 * Returns the matches for the type and name argument, ordered by match
	 * quality.
	 * 
	 * @param expectedType
	 *            - the qualified type of the parameter we are trying to match
	 * @param paramName
	 *            - the name of the parameter (used to find similarly named
	 *            matches)
	 * @param pos
	 * @param suggestions
	 *            the suggestions or <code>null</code>
	 * @param fillBestGuess
	 * @return returns the name of the best match, or <code>null</code> if no
	 *         match found
	 * @throws JavaModelException
	 *             if it fails
	 */
	public ICompletionProposal[] parameterProposals(String initialValue,
			String paramName, Position pos, boolean fillBestGuess)
			throws ModelException {
		List<String> typeMatches = new ArrayList<String>();
		if (initialValue != null) {
			typeMatches.add(initialValue);
		}
		ICompletionProposal[] ret = new ICompletionProposal[typeMatches.size()];
		int i = 0;
		int replacementLength = 0;
		for (Iterator<String> it = typeMatches.iterator(); it.hasNext();) {
			String name = it.next();
			if (i == 0) {
				replacementLength = name.length();
			}

			final char[] triggers = new char[1];
			triggers[triggers.length - 1] = ';';

			ret[i++] = new PositionBasedCompletionProposal(name, pos,
					replacementLength, getImage(), name, null, null, triggers);
		}
		if (!fillBestGuess) {
			// insert a proposal with the argument name
			ICompletionProposal[] extended = new ICompletionProposal[ret.length + 1];
			System.arraycopy(ret, 0, extended, 1, ret.length);
			extended[0] = new PositionBasedCompletionProposal(paramName, pos,
					replacementLength/* paramName.length() */, null, paramName,
					null, null, NO_TRIGGERS);
			return extended;
		}
		return ret;
	}

}