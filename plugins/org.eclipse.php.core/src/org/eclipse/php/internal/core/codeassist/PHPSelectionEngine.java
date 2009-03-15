/*******************************************************************************
 * Copyright (c) 2005, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.php.internal.core.codeassist;

import java.util.*;

import org.eclipse.core.resources.IFile;
import org.eclipse.dltk.ast.ASTNode;
import org.eclipse.dltk.ast.declarations.ModuleDeclaration;
import org.eclipse.dltk.ast.expressions.Expression;
import org.eclipse.dltk.ast.references.SimpleReference;
import org.eclipse.dltk.ast.references.TypeReference;
import org.eclipse.dltk.ast.references.VariableReference;
import org.eclipse.dltk.codeassist.IAssistParser;
import org.eclipse.dltk.codeassist.ScriptSelectionEngine;
import org.eclipse.dltk.core.*;
import org.eclipse.dltk.internal.core.AbstractSourceModule;
import org.eclipse.dltk.internal.core.SourceRefElement;
import org.eclipse.dltk.ti.IContext;
import org.eclipse.dltk.ti.ISourceModuleContext;
import org.eclipse.dltk.ti.types.IEvaluatedType;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.php.internal.core.PHPCorePlugin;
import org.eclipse.php.internal.core.compiler.ast.nodes.*;
import org.eclipse.php.internal.core.compiler.ast.parser.ASTUtils;
import org.eclipse.php.internal.core.documentModel.parser.PHPRegionContext;
import org.eclipse.php.internal.core.documentModel.parser.regions.IPhpScriptRegion;
import org.eclipse.php.internal.core.documentModel.parser.regions.PHPRegionTypes;
import org.eclipse.php.internal.core.documentModel.partitioner.PHPPartitionTypes;
import org.eclipse.php.internal.core.typeinference.PHPClassType;
import org.eclipse.php.internal.core.typeinference.PHPModelUtils;
import org.eclipse.php.internal.core.typeinference.PHPTypeInferenceUtils;
import org.eclipse.php.internal.core.util.text.PHPTextSequenceUtilities;
import org.eclipse.php.internal.core.util.text.TextSequence;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.text.*;

public class PHPSelectionEngine extends ScriptSelectionEngine {

	private static final String OPEN_BRACE = "(";
	private static final String PROTECTED = "protected";
	private static final String PUBLIC = "public";
	private static final String PAAMAYIM_NEKUDOTAIM = "::";
	private static final String CONST = "const";
	private static final String THIS = "this";
	private static final String STATIC = "static";
	private static final String PRIVATE = "private";
	private static final String VAR = "var";
	private static final String IMPLEMENTS = "implements";
	private static final String EXTENDS = "extends";
	private static final String NEW = "new";
	private static final String INTERFACE = "interface";
	private static final String CLASS = "class";
	private static final String FUNCTION = "function";
	private static final IModelElement[] EMPTY = {};

	public IAssistParser getParser() {
		return null;
	}

	public IModelElement[] select(org.eclipse.dltk.compiler.env.ISourceModule sourceUnit, int offset, int end) {

		if (end < offset) {
			end = offset + 1;
		}

		ISourceModule sourceModule = (ISourceModule) sourceUnit.getModelElement();

		// First, try to resolve using AST (if we have parsed it well):
		try {
			IModelElement[] elements = internalASTResolve(sourceModule, offset, end);
			if (elements != null) {
				return elements;
			}
		} catch (Exception e) {
			if (DLTKCore.DEBUG_SELECTION) {
				e.printStackTrace();
			}
		}

		// Use the old way by playing with document & buffer:
		IStructuredDocument document = null;
		IStructuredModel structuredModel = null;
		try {
			IFile file = (IFile) sourceUnit.getModelElement().getResource();
			if (file != null) {
				if (file.exists()) {
					structuredModel = StructuredModelManager.getModelManager().getExistingModelForRead(file);
					if (structuredModel != null) {
						document = structuredModel.getStructuredDocument();
					} else {
						document = StructuredModelManager.getModelManager().createStructuredDocumentFor(file);
					}
				} else {
					document = StructuredModelManager.getModelManager().createNewStructuredDocumentFor(file);
					document.set(sourceUnit.getSourceContents());
				}
			}
		} catch (Exception e) {
			if (DLTKCore.DEBUG_SELECTION) {
				e.printStackTrace();
			}
		} finally {
			if (structuredModel != null) {
				structuredModel.releaseFromRead();
			}
		}

		if (document != null) {
			return internalResolve(document, sourceModule, offset, end);
		}

		return EMPTY;
	}

	private IModelElement[] internalASTResolve(ISourceModule sourceModule, int offset, int end) throws ModelException {

		String source = sourceModule.getSource();
		offset = PHPTextSequenceUtilities.readIdentifierStartIndex(source, offset, true);
		end = PHPTextSequenceUtilities.readIdentifierEndIndex(source, end, true);

		int methodEnd = PHPTextSequenceUtilities.getMethodEndIndex(source, end);
		if (methodEnd != -1) {
			end = methodEnd;
		}

		ModuleDeclaration parsedUnit = SourceParserUtil.getModuleDeclaration(sourceModule, null);

		ASTNode node = ASTUtils.findMinimalNode(parsedUnit, offset, end);
		if (node != null) {

			IContext context = ASTUtils.findContext(sourceModule, parsedUnit, node);
			if (context != null) {

				// Function call:
				if (node instanceof PHPCallExpression) {
					PHPCallExpression callExpression = (PHPCallExpression) node;
					if (callExpression.getReceiver() != null) {
						IEvaluatedType receiverType = PHPTypeInferenceUtils.resolveExpression(sourceModule, parsedUnit, context, callExpression.getReceiver());
						if (receiverType != null) {
							IModelElement[] elements = PHPTypeInferenceUtils.getModelElements(receiverType, (ISourceModuleContext) context);
							List<IModelElement> methods = new LinkedList<IModelElement>();
							if (elements != null) {
								for (IModelElement element : elements) {
									if (element instanceof IType) {
										IType type = (IType) element;
										methods.addAll(Arrays.asList(CodeAssistUtils.getClassMethods(type, callExpression.getName(), CodeAssistUtils.EXACT_NAME)));
									}
								}
							}
							return methods.toArray(new IModelElement[methods.size()]);
						}
					} else {
						return PHPModelUtils.filterElements(sourceModule, CodeAssistUtils.getGlobalMethods(sourceModule, callExpression.getName(), CodeAssistUtils.EXACT_NAME));
					}
				}
				// Static field or constant access:
				else if (node instanceof StaticDispatch) {
					StaticDispatch dispatch = (StaticDispatch) node;
					String fieldName = null;
					if (dispatch instanceof StaticConstantAccess) {
						fieldName = ((StaticConstantAccess) dispatch).getConstant().getName();
					} else if (dispatch instanceof StaticFieldAccess) {
						ASTNode field = ((StaticFieldAccess) dispatch).getField();
						if (field instanceof VariableReference) {
							fieldName = ((VariableReference) field).getName();
						}
					}
					if (fieldName != null && dispatch.getDispatcher() != null) {
						IEvaluatedType dispatcherType = PHPTypeInferenceUtils.resolveExpression(sourceModule, parsedUnit, context, dispatch.getDispatcher());
						if (dispatcherType != null) {
							IModelElement[] elements = PHPTypeInferenceUtils.getModelElements(dispatcherType, (ISourceModuleContext) context);
							List<IModelElement> fields = new LinkedList<IModelElement>();
							if (elements != null) {
								for (IModelElement element : elements) {
									if (element instanceof IType) {
										IType type = (IType) element;
										try {
											fields.addAll(Arrays.asList(getClassField(type, fieldName)));
										} catch (ModelException e) {
											if (DLTKCore.DEBUG_SELECTION) {
												e.printStackTrace();
											}
										}
									}
								}
							}
							return fields.toArray(new IModelElement[fields.size()]);
						}
					}
				}
				// Dynamic field access:
				else if (node instanceof FieldAccess) {
					FieldAccess fieldAccess = (FieldAccess) node;
					ASTNode field = fieldAccess.getField();
					String fieldName = null;
					if (field instanceof SimpleReference) {
						fieldName = ((SimpleReference) field).getName();
					}
					if (fieldName != null && fieldAccess.getDispatcher() != null) {
						IEvaluatedType dispatcherType = PHPTypeInferenceUtils.resolveExpression(sourceModule, parsedUnit, context, fieldAccess.getDispatcher());
						if (dispatcherType != null) {
							IModelElement[] elements = PHPTypeInferenceUtils.getModelElements(dispatcherType, (ISourceModuleContext) context);
							List<IModelElement> fields = new LinkedList<IModelElement>();
							if (elements != null) {
								for (IModelElement element : elements) {
									if (element instanceof IType) {
										IType type = (IType) element;
										try {
											fields.addAll(Arrays.asList(getClassField(type, fieldName)));
											fields.addAll(Arrays.asList(getClassField(type, '$' + fieldName)));
										} catch (ModelException e) {
											if (DLTKCore.DEBUG_SELECTION) {
												e.printStackTrace();
											}
										}
									}
								}
							}
							return fields.toArray(new IModelElement[fields.size()]);
						}
					}
				}
				// Class/Interface reference:
				else if (node instanceof TypeReference) {
					return PHPTypeInferenceUtils.getModelElements(new PHPClassType(((TypeReference) node).getName()), (ISourceModuleContext) context);
				}
				else if (node instanceof ClassInstanceCreation) {
					ClassInstanceCreation newNode = (ClassInstanceCreation) node;
					Expression className = newNode.getClassName();
					if (className instanceof SimpleReference) {
						return PHPTypeInferenceUtils.getModelElements(new PHPClassType(((SimpleReference) className).getName()), (ISourceModuleContext) context);
					}
				}
			}
		}
		return null;
	}

	private IModelElement[] internalResolve(IStructuredDocument sDoc, ISourceModule sourceModule, int offset, int end) {
		try {
			IStructuredDocumentRegion sRegion = sDoc.getRegionAtCharacterOffset(offset);
			if (sRegion != null) {
				ITextRegion tRegion = sRegion.getRegionAtCharacterOffset(offset);

				ITextRegionCollection container = sRegion;
				if (tRegion instanceof ITextRegionContainer) {
					container = (ITextRegionContainer) tRegion;
					tRegion = container.getRegionAtCharacterOffset(offset);
				}

				if (tRegion.getType() == PHPRegionContext.PHP_CONTENT) {
					IPhpScriptRegion phpScriptRegion = (IPhpScriptRegion) tRegion;
					tRegion = phpScriptRegion.getPhpToken(offset - container.getStartOffset() - phpScriptRegion.getStart());

					// Determine element name:
					int elementStart = container.getStartOffset() + phpScriptRegion.getStart() + tRegion.getStart();
					TextSequence statement = PHPTextSequenceUtilities.getStatement(elementStart + tRegion.getLength(), sRegion, true);
					int endPosition = PHPTextSequenceUtilities.readBackwardSpaces(statement, statement.length());
					int startPosition = PHPTextSequenceUtilities.readIdentifierStartIndex(statement, endPosition, true);
					String elementName = statement.subSequence(startPosition, endPosition).toString();

					// Determine previous word:
					int prevWordEnd = PHPTextSequenceUtilities.readBackwardSpaces(statement, startPosition);
					int prevWordStart = PHPTextSequenceUtilities.readIdentifierStartIndex(statement, prevWordEnd, false);
					String prevWord = statement.subSequence(prevWordStart, prevWordEnd).toString();

					// Determine next word:
					ITextRegion nextRegion = tRegion;
					do {
						nextRegion = phpScriptRegion.getPhpToken(nextRegion.getEnd());
						if (!PHPPartitionTypes.isPHPCommentState(nextRegion.getType()) && nextRegion.getType() != PHPRegionTypes.WHITESPACE) {
							break;
						}
					} while (nextRegion.getEnd() < phpScriptRegion.getLength());

					String nextWord = sDoc.get(container.getStartOffset() + phpScriptRegion.getStart() + nextRegion.getStart(), nextRegion.getTextLength());

					if (elementName.length() > 0) {
						IType containerClass = CodeAssistUtils.getContainerClassData(sourceModule, offset);

						// If we are in function declaration:
						if (FUNCTION.equalsIgnoreCase(prevWord)) { //$NON-NLS-1$
							if (containerClass != null) {
								return getClassMethod(containerClass, elementName);
							}
							return getFunction(sourceModule, elementName);
						}

						// If we are in class declaration:
						if (CLASS.equalsIgnoreCase(prevWord) || INTERFACE.equalsIgnoreCase(prevWord)) { //$NON-NLS-1$ //$NON-NLS-2$
							return getClass(sourceModule, elementName);
						}

						// Class instantiation:
						if (NEW.equalsIgnoreCase(prevWord)) { //$NON-NLS-1$
							return CodeAssistUtils.getGlobalTypes(sourceModule, elementName, CodeAssistUtils.EXACT_NAME | CodeAssistUtils.ONLY_CLASSES);
						}

						// Handle extends and implements:
						// Check that the statement suites the condition. If class or interface keywords don't appear in the beginning of the statement or they are alone there.
						boolean isClassDeclaration = false;
						if (statement.length() > 6 && (CLASS.equals(statement.subSequence(0, 5).toString()) && (isClassDeclaration = true) || statement.length() > 10 && INTERFACE.equals(statement.subSequence(0, 9).toString()))) { //$NON-NLS-1$ //$NON-NLS-2$

							IModelElement[] generalizationTypes = getGeneralizationTypes(sourceModule, isClassDeclaration, prevWord, elementName);
							if (generalizationTypes != null) {
								return generalizationTypes;
							}

							// Multiple extensions and implementations:
							int listStartPosition = PHPTextSequenceUtilities.readIdentifierListStartIndex(statement, endPosition);

							// Determine pre-list word:
							int preListWordEnd = PHPTextSequenceUtilities.readBackwardSpaces(statement, listStartPosition);
							int preListWordStart = PHPTextSequenceUtilities.readIdentifierStartIndex(statement, preListWordEnd, false);
							String preListWord = statement.subSequence(preListWordStart, preListWordEnd).toString();

							generalizationTypes = getGeneralizationTypes(sourceModule, isClassDeclaration, preListWord, elementName);
							if (generalizationTypes != null) {
								return generalizationTypes;
							}
						}

						// Previous trigger:
						String trigger = null;
						if (startPosition > 2) {
							trigger = statement.subSequence(startPosition - 2, startPosition).toString();
						}

						// If this is variable:
						if (elementName.charAt(0) == '$' && !PAAMAYIM_NEKUDOTAIM.equals(trigger)) { //$NON-NLS-1$
							// Don't show escaped variables within PHP string:
							if (PHPPartitionTypes.isPHPQuotesState(tRegion.getType())) {
								try {
									char charBefore = sDoc.get(elementStart - 2, 1).charAt(0);
									if (charBefore == '\\') {
										return EMPTY;
									}
								} catch (BadLocationException e) {
									PHPCorePlugin.log(e);
								}
							}

							// If we are in var definition:
							if (containerClass != null) {
								if (VAR.equalsIgnoreCase(prevWord) || PRIVATE.equalsIgnoreCase(prevWord) || STATIC.equalsIgnoreCase(prevWord) || PUBLIC.equalsIgnoreCase(prevWord) || PROTECTED.equalsIgnoreCase(prevWord)) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

									return getClassField(containerClass, elementName);
								}
								if (THIS.equalsIgnoreCase(elementName)) { //$NON-NLS-1$
									return new IModelElement[] { containerClass };
								}
							}

							IModelElement[] elements = CodeAssistUtils.getGlobalOrMethodFields(sourceModule, offset, elementName, CodeAssistUtils.EXACT_NAME);
							return PHPModelUtils.filterElements(sourceModule, elements);
						}

						// If we are at class constant definition:
						if (containerClass != null) {
							if (CONST.equalsIgnoreCase(prevWord)) { //$NON-NLS-1$
								return getClassField(containerClass, elementName);
							}
						}

						// We are at class trigger:
						if (PAAMAYIM_NEKUDOTAIM.equals(nextWord)) { //$NON-NLS-1$
							return PHPModelUtils.filterElements(sourceModule, CodeAssistUtils.getGlobalTypes(sourceModule, elementName, CodeAssistUtils.EXACT_NAME));
						}

						IType[] types = CodeAssistUtils.getTypesFor(sourceModule, statement, startPosition, offset, sDoc.getLineOfOffset(offset));

						// Is it function or method:
						if (OPEN_BRACE.equals(nextWord) || PHPPartitionTypes.isPHPDocState(tRegion.getType())) { //$NON-NLS-1$
							if (types != null && types.length > 0) {
								List<IMethod> methods = new LinkedList<IMethod>();
								for (IType t : types) {
									methods.addAll(Arrays.asList(CodeAssistUtils.getClassMethods(t, elementName, CodeAssistUtils.EXACT_NAME)));
								}
								return methods.toArray(new IMethod[methods.size()]);
							}
							return PHPModelUtils.filterElements(sourceModule, CodeAssistUtils.getGlobalMethods(sourceModule, elementName, CodeAssistUtils.EXACT_NAME));
						}

						if (types != null && types.length > 0) {
							// Check whether this is a class constant:
							if (startPosition > 0) {
								if (PAAMAYIM_NEKUDOTAIM.equals(trigger) && elementName.charAt(0) != '$') { //$NON-NLS-1$
									List<IModelElement> fields = new LinkedList<IModelElement>();
									for (IType t : types) {
										fields.addAll(Arrays.asList(getClassField(t, elementName)));
									}
									return fields.toArray(new IModelElement[fields.size()]);
								}
							}

							// What can it be? Only class variables:
							Set<IModelElement> fields = new TreeSet<IModelElement>(new Comparator<IModelElement>() {
								public int compare(IModelElement o1, IModelElement o2) {
									try {
										ISourceRange r1 = ((SourceRefElement)o1).getSourceRange();
										ISourceRange r2 = ((SourceRefElement)o2).getSourceRange();
										return (int) Math.signum(r1.getOffset() - r2.getOffset());
									} catch (ModelException e) {
										if (DLTKCore.DEBUG_SELECTION) {
											e.printStackTrace();
										}
									}
									return 0;
								}
							});
							for (IType t : types) {
								fields.addAll(Arrays.asList(CodeAssistUtils.getClassFields(t, elementName, CodeAssistUtils.EXACT_NAME)));
							}
							return fields.toArray(new IModelElement[fields.size()]);
						}

						// This can be only global constant, if we've reached here:
						IModelElement[] constants = PHPModelUtils.filterElements(sourceModule, CodeAssistUtils.getGlobalFields(sourceModule, elementName, CodeAssistUtils.EXACT_NAME));
						if (constants.length > 0) {
							return constants;
						}

						// Return class if nothing else found.
						IModelElement[] elements = CodeAssistUtils.getGlobalTypes(sourceModule, elementName, CodeAssistUtils.EXACT_NAME);
						return PHPModelUtils.filterElements(sourceModule, elements);
					}
				}
			}
		} catch (Exception e) {
			if (DLTKCore.DEBUG_SELECTION) {
				e.printStackTrace();
			}
		}
		return EMPTY;
	}

	private static IModelElement[] getGeneralizationTypes(ISourceModule sourceModule, boolean isClassDeclaration, String generalization, String elementName) {
		if (EXTENDS.equalsIgnoreCase(generalization)) {
			if (isClassDeclaration) {
				return CodeAssistUtils.getGlobalTypes(sourceModule, elementName, CodeAssistUtils.EXACT_NAME | CodeAssistUtils.ONLY_CLASSES);
			}
			return CodeAssistUtils.getGlobalTypes(sourceModule, elementName, CodeAssistUtils.EXACT_NAME | CodeAssistUtils.ONLY_INTERFACES);
		}
		if (IMPLEMENTS.equalsIgnoreCase(generalization)) { //$NON-NLS-1$ //$NON-NLS-2$
			return CodeAssistUtils.getGlobalTypes(sourceModule, elementName, CodeAssistUtils.EXACT_NAME | CodeAssistUtils.ONLY_INTERFACES);
		}
		return null;
	}

	private static IModelElement[] getClassField(IType type, String elementName) throws ModelException {
		IField[] fields = type.getFields();
		for (IField field : fields) {
			if (field.getElementName().equalsIgnoreCase(elementName)) {
				return new IModelElement[] { field };
			}
		}
		return EMPTY;
	}

	private static IModelElement[] getClassMethod(IType type, String elementName) throws ModelException {
		IMethod[] methods = type.getMethods();
		for (IMethod method : methods) {
			if (method.getElementName().equalsIgnoreCase(elementName)) {
				return new IModelElement[] { method };
			}
		}
		return EMPTY;
	}

	private static IModelElement[] getFunction(ISourceModule sourceModule, String elementName) throws ModelException {
		IMethod[] methods = ((AbstractSourceModule)sourceModule).getMethods();
		for (IMethod method : methods) {
			if (method.getElementName().equalsIgnoreCase(elementName)) {
				return new IModelElement[] { method };
			}
		}
		return EMPTY;
	}

	private static IModelElement[] getClass(ISourceModule sourceModule, String elementName) throws ModelException {
		IType[] types = sourceModule.getTypes();
		for (IType type : types) {
			if (type.getElementName().equalsIgnoreCase(elementName)) {
				return new IModelElement[] { type };
			}
		}
		return EMPTY;
	}
}
