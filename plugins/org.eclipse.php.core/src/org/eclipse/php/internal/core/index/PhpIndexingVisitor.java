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
package org.eclipse.php.internal.core.index;

import java.util.*;
import java.util.regex.Pattern;

import org.eclipse.dltk.ast.ASTListNode;
import org.eclipse.dltk.ast.ASTNode;
import org.eclipse.dltk.ast.ASTVisitor;
import org.eclipse.dltk.ast.Modifiers;
import org.eclipse.dltk.ast.declarations.*;
import org.eclipse.dltk.ast.expressions.CallArgumentsList;
import org.eclipse.dltk.ast.expressions.CallExpression;
import org.eclipse.dltk.ast.expressions.Expression;
import org.eclipse.dltk.ast.references.ConstantReference;
import org.eclipse.dltk.ast.references.SimpleReference;
import org.eclipse.dltk.ast.references.TypeReference;
import org.eclipse.dltk.ast.references.VariableReference;
import org.eclipse.dltk.ast.statements.Statement;
import org.eclipse.dltk.core.IModelElement;
import org.eclipse.dltk.core.index2.IIndexingRequestor;
import org.eclipse.php.internal.core.compiler.IPHPModifiers;
import org.eclipse.php.internal.core.compiler.ast.nodes.*;
import org.eclipse.php.internal.core.compiler.ast.parser.ASTUtils;

/**
 * PHP indexing visitor for H2 database
 * 
 * @author michael
 * 
 */
public class PhpIndexingVisitor extends ASTVisitor {

	private IIndexingRequestor requestor;

	public PhpIndexingVisitor(IIndexingRequestor requestor) {
		this.requestor = requestor;
	}

	private static final String CONSTRUCTOR_NAME = "__construct"; //$NON-NLS-1$
	private static final Pattern WHITESPACE_SEPERATOR = Pattern.compile("\\s+"); //$NON-NLS-1$

	/**
	 * This should replace the need for fInClass, fInMethod and fCurrentMethod
	 * since in php the type declarations can be nested.
	 */
	protected Stack<Declaration> declarations = new Stack<Declaration>();

	/**
	 * Deferred elements that where declared in method/function but should
	 * belong to the global scope.
	 */
	protected List<ASTNode> deferredDeclarations = new LinkedList<ASTNode>();

	/**
	 * This stack contains a set per method, where each set contains all global
	 * variables names delcared through 'global' keyword inside this method.
	 */
	protected Stack<Set<String>> methodGlobalVars = new Stack<Set<String>>();

	protected NamespaceDeclaration fCurrentNamespace;
	protected String fCurrentQualifier;
	protected String fCurrentParent;
	protected Stack<ASTNode> fNodes = new Stack<ASTNode>(); // Used to hold

	// visited nodes

	public MethodDeclaration getCurrentMethod() {
		Declaration currDecleration = declarations.peek();
		if (currDecleration instanceof MethodDeclaration) {
			return (MethodDeclaration) currDecleration;
		}
		return null;
	}

	public boolean endvisit(MethodDeclaration method) throws Exception {
		methodGlobalVars.pop();
		declarations.pop();
		endvisitGeneral(method);
		return true;
	}

	public boolean endvisit(TypeDeclaration type) throws Exception {
		if (type instanceof NamespaceDeclaration) {
			NamespaceDeclaration namespaceDecl = (NamespaceDeclaration) type;
			fCurrentNamespace = null; // there are no nested namespaces
			fCurrentQualifier = null;
			if (namespaceDecl.isGlobal()) {
				return visitGeneral(type);
			}
		} else {
			fCurrentParent = null;
		}
		declarations.pop();

		// resolve more type member declarations
		resolveMagicMembers(type);

		endvisitGeneral(type);
		return true;
	}

	@SuppressWarnings("unchecked")
	public boolean visit(MethodDeclaration method) throws Exception {
		fNodes.push(method);
		methodGlobalVars.add(new HashSet<String>());

		Declaration parentDeclaration = null;
		if (!declarations.empty()) {
			parentDeclaration = declarations.peek();
		}
		declarations.push(method);

		// In case we are entering a nested element - just add to the deferred
		// list
		// and get out of the nested element visiting process
		if (parentDeclaration instanceof MethodDeclaration) {
			deferredDeclarations.add(method);
			return visitGeneral(method);
		}

		if (parentDeclaration instanceof InterfaceDeclaration) {
			method.setModifier(Modifiers.AccAbstract);
		}

		int modifiers = method.getModifiers();
		String methodName = method.getName();

		// Determine whether this method represents constructor:
		if (methodName.equalsIgnoreCase(CONSTRUCTOR_NAME)
				|| (parentDeclaration instanceof ClassDeclaration && methodName
						.equalsIgnoreCase(((ClassDeclaration) parentDeclaration)
								.getName()))) {
			modifiers |= IPHPModifiers.Constructor;
		}

		// Check whether this method is marked as @internal
		if (method instanceof IPHPDocAwareDeclaration) {
			IPHPDocAwareDeclaration phpDocAwareDeclaration = (IPHPDocAwareDeclaration) method;
			PHPDocBlock phpDoc = phpDocAwareDeclaration.getPHPDoc();
			if (phpDoc != null && phpDoc.getTags(PHPDocTag.INTERNAL).length > 0) {
				modifiers |= IPHPModifiers.Internal;
			}
		}

		if (parentDeclaration == null
				|| (parentDeclaration instanceof TypeDeclaration && parentDeclaration == fCurrentNamespace)) {
			modifiers |= Modifiers.AccGlobal;
		}

		StringBuilder metadata = new StringBuilder();
		List<Argument> arguments = method.getArguments();
		if (arguments != null) {
			Iterator<Argument> i = arguments.iterator();
			while (i.hasNext()) {
				Argument arg = (Argument) i.next();
				metadata.append(arg.getName());
				if (i.hasNext()) {
					metadata.append(",");
				}
			}
		}

		// Add method declaration:
		requestor.addDeclaration(IModelElement.METHOD, modifiers, method
				.sourceStart(), method.sourceEnd() - method.sourceStart(),
				method.getNameStart(), method.getNameEnd()
						- method.getNameStart(), methodName,
				metadata.length() == 0 ? null : metadata.toString(),
				fCurrentQualifier, fCurrentParent);

		return visitGeneral(method);
	}

	public boolean visit(TypeDeclaration type) throws Exception {
		boolean isNamespace = false;
		if (type instanceof NamespaceDeclaration) {
			NamespaceDeclaration namespaceDecl = (NamespaceDeclaration) type;
			fCurrentNamespace = namespaceDecl;
			if (namespaceDecl.isGlobal()) {
				return visitGeneral(type);
			}
			isNamespace = true;
		}

		Declaration parentDeclaration = null;
		if (!declarations.empty()) {
			parentDeclaration = declarations.peek();
		}
		declarations.push(type);

		if (!(parentDeclaration instanceof NamespaceDeclaration)) {
			type.setModifier(Modifiers.AccGlobal);
		}

		// In case we are entering a nested element
		if (parentDeclaration instanceof MethodDeclaration) {
			deferredDeclarations.add(type);
			return visitGeneral(type);
		}

		int modifiers = type.getModifiers();

		// Check whether this class is marked as @internal
		if (type instanceof IPHPDocAwareDeclaration) {
			IPHPDocAwareDeclaration phpDocAwareDeclaration = (IPHPDocAwareDeclaration) type;
			PHPDocBlock phpDoc = phpDocAwareDeclaration.getPHPDoc();
			if (phpDoc != null && phpDoc.getTags(PHPDocTag.INTERNAL).length > 0) {
				modifiers |= IPHPModifiers.Internal;
			}
		}

		// check whether this is a namespace
		if (isNamespace) {
			modifiers |= Modifiers.AccNameSpace;
			fCurrentQualifier = type.getName();
		} else {
			fCurrentParent = type.getName();
		}

		String[] superClasses = processSuperClasses(type);
		StringBuilder metadata = new StringBuilder();
		for (int i = 0; i < superClasses.length; ++i) {
			metadata.append(superClasses[i]);
			if (i < superClasses.length - 1) {
				metadata.append(",");
			}
		}

		requestor.addDeclaration(IModelElement.TYPE, modifiers, type
				.sourceStart(), type.sourceEnd() - type.sourceStart(), type
				.getNameStart(), type.getNameEnd() - type.getNameStart(), type
				.getName(),
				metadata.length() == 0 ? null : metadata.toString(),
				isNamespace ? null : fCurrentQualifier, null);

		return visitGeneral(type);
	}

	@SuppressWarnings("unchecked")
	protected String[] processSuperClasses(TypeDeclaration type) {
		ASTListNode superClasses = type.getSuperClasses();
		if (superClasses == null) {
			return new String[] {};
		}
		List superClassNames = superClasses.getChilds();
		List<String> result = new ArrayList<String>(superClassNames.size());
		Iterator iterator = superClassNames.iterator();
		while (iterator.hasNext()) {
			Object nameNode = iterator.next();
			String name;
			if (nameNode instanceof FullyQualifiedReference) {
				FullyQualifiedReference fullyQualifiedName = (FullyQualifiedReference) nameNode;
				name = fullyQualifiedName.getFullyQualifiedName();
				if (fullyQualifiedName.getNamespace() != null) {
					if (name.charAt(0) == NamespaceReference.NAMESPACE_SEPARATOR) {
						name = name.substring(1);
					}
				} else {
					if (fCurrentNamespace != null) {
						name = new StringBuilder(fCurrentNamespace.getName())
								.append(NamespaceReference.NAMESPACE_SEPARATOR)
								.append(name).toString();
					}
				}
				result.add(name);
			} else if (nameNode instanceof SimpleReference) {
				result.add(((SimpleReference) nameNode).getName());
			}
		}
		return (String[]) result.toArray(new String[result.size()]);
	}

	/**
	 * Resolve class members that were defined using the @property tag
	 * 
	 * @param type
	 *            declaration for wich we add the magic variables
	 */
	private void resolveMagicMembers(TypeDeclaration type) {
		if (type instanceof IPHPDocAwareDeclaration) {
			IPHPDocAwareDeclaration declaration = (IPHPDocAwareDeclaration) type;
			final PHPDocBlock doc = declaration.getPHPDoc();
			if (doc != null) {
				final PHPDocTag[] tags = doc.getTags();
				for (PHPDocTag docTag : tags) {
					final int tagKind = docTag.getTagKind();
					if (tagKind == PHPDocTag.PROPERTY
							|| tagKind == PHPDocTag.PROPERTY_READ
							|| tagKind == PHPDocTag.PROPERTY_WRITE) {
						// http://manual.phpdoc.org/HTMLSmartyConverter/HandS/phpDocumentor/tutorial_tags.property.pkg.html
						final String[] split = WHITESPACE_SEPERATOR
								.split(docTag.getValue().trim());
						if (split.length < 2) {
							break;
						}

						String name = removeParenthesis(split);
						int offset = docTag.sourceStart();
						int length = docTag.sourceStart() + 9;
						requestor.addDeclaration(IModelElement.FIELD,
								Modifiers.AccPublic, offset, length, offset,
								length, name, null, fCurrentQualifier,
								fCurrentParent);

					} else if (tagKind == PHPDocTag.METHOD) {
						// http://manual.phpdoc.org/HTMLSmartyConverter/HandS/phpDocumentor/tutorial_tags.method.pkg.html
						final String[] split = WHITESPACE_SEPERATOR
								.split(docTag.getValue().trim());
						if (split.length < 2) {
							break;
						}

						String name = removeParenthesis(split);
						int offset = docTag.sourceStart();
						int length = docTag.sourceStart() + 6;
						requestor.addDeclaration(IModelElement.METHOD,
								Modifiers.AccPublic, offset, length, offset,
								length, name, null, fCurrentQualifier,
								fCurrentParent);
					}
				}
			}
		}
	}

	private String removeParenthesis(final String[] split) {
		final String name = split[1];
		return name.endsWith("()") ? name.substring(0, name.length() - 2)
				: name;
	}

	public boolean visit(FieldDeclaration decl) throws Exception {
		// This is constant declaration:
		int modifiers = decl.getModifiers();
		requestor.addDeclaration(IModelElement.FIELD, modifiers, decl
				.sourceStart(), decl.sourceEnd() - decl.sourceStart(), decl
				.getNameStart(), decl.getNameEnd() - decl.getNameStart(), decl
				.getName(), null, null, null);

		return visitGeneral(decl);
	}

	public boolean endvisit(FieldDeclaration declaration) throws Exception {
		endvisitGeneral(declaration);
		return true;
	}

	public boolean visit(PHPFieldDeclaration decl) throws Exception {
		// This is variable declaration:
		int modifiers = decl.getModifiers();

		requestor.addDeclaration(IModelElement.FIELD, modifiers, decl
				.sourceStart(), decl.sourceEnd() - decl.sourceStart(), decl
				.getNameStart(), decl.getNameEnd() - decl.getNameStart(), decl
				.getName(), null, fCurrentQualifier, fCurrentParent);

		return visitGeneral(decl);
	}

	public boolean endvisit(PHPFieldDeclaration declaration) throws Exception {
		endvisitGeneral(declaration);
		return true;
	}

	public boolean visit(CallExpression call) throws Exception {
		FieldDeclaration constantDecl = ASTUtils.getConstantDeclaration(call);
		if (constantDecl != null) {
			// In case we are entering a nested element
			if (!declarations.empty()
					&& declarations.peek() instanceof MethodDeclaration) {
				deferredDeclarations.add(constantDecl);
				return visitGeneral(call);
			}

			visit((FieldDeclaration) constantDecl);

		} else {
			int argsCount = 0;
			CallArgumentsList args = call.getArgs();
			if (args != null && args.getChilds() != null) {
				argsCount = args.getChilds().size();
			}

			requestor.addReference(IModelElement.METHOD, call.sourceStart(),
					call.sourceEnd() - call.sourceStart(), call.getName(),
					Integer.toString(argsCount), null);
		}

		return visitGeneral(call);
	}

	public boolean visit(Include include) throws Exception {
		// special case for include statements; we need to cache this
		// information in order to access it quickly:
		if (include.getExpr() instanceof Scalar) {
			Scalar filePath = (Scalar) include.getExpr();
			requestor.addReference(IModelElement.METHOD,
					filePath.sourceStart(), filePath.sourceEnd()
							- filePath.sourceStart(), "include", Integer
							.toString(1), null);

			String fullPath = ASTUtils.stripQuotes(((Scalar) filePath)
					.getValue());
			int idx = Math.max(fullPath.lastIndexOf('/'), fullPath
					.lastIndexOf('\\'));

			String lastSegment = fullPath;
			if (idx != -1) {
				lastSegment = lastSegment.substring(idx + 1);
			}
			requestor.addDeclaration(IModelElement.IMPORT_DECLARATION, 0,
					include.sourceStart(), include.sourceEnd()
							- include.sourceStart(), filePath.sourceStart(),
					filePath.sourceEnd() - filePath.sourceStart(), lastSegment,
					fullPath, null, null);
		}

		return visitGeneral(include);
	}

	public boolean visit(ConstantDeclaration declaration) throws Exception {
		int modifiers = Modifiers.AccConstant | Modifiers.AccPublic
				| Modifiers.AccFinal;

		ConstantReference constantName = declaration.getConstantName();
		int offset = constantName.sourceStart();
		int length = constantName.sourceEnd();
		requestor.addDeclaration(IModelElement.FIELD, modifiers, offset,
				length, offset, length, ASTUtils.stripQuotes(constantName
						.getName()), null, fCurrentQualifier, fCurrentParent);
		return visitGeneral(declaration);
	}

	public boolean endvisit(ConstantDeclaration declaration) throws Exception {
		endvisitGeneral(declaration);
		return true;
	}

	public boolean visit(Assignment assignment) throws Exception {
		Expression left = assignment.getVariable();
		if (left instanceof FieldAccess) { // class variable ($this->a = .)
			FieldAccess fieldAccess = (FieldAccess) left;
			Expression dispatcher = fieldAccess.getDispatcher();
			if (dispatcher instanceof VariableReference
					&& "$this".equals(((VariableReference) dispatcher).getName())) { //$NON-NLS-1$
				Expression field = fieldAccess.getField();
				if (field instanceof SimpleReference) {
					SimpleReference var = (SimpleReference) field;
					int modifiers = Modifiers.AccPublic;
					int offset = var.sourceStart();
					int length = var.sourceEnd() - offset;
					requestor.addDeclaration(IModelElement.FIELD, modifiers,
							offset, length, offset, length,
							'$' + var.getName(), null, fCurrentQualifier,
							fCurrentParent);
				}
			}
		} else if (left instanceof VariableReference) {
			int modifiers = Modifiers.AccPublic | Modifiers.AccGlobal;
			if (!declarations.empty()
					&& declarations.peek() instanceof MethodDeclaration
					&& !methodGlobalVars.peek().contains(
							((VariableReference) left).getName())) {
				return visitGeneral(assignment);
			}
			int offset = left.sourceStart();
			int length = left.sourceEnd() - offset;
			requestor.addDeclaration(IModelElement.FIELD, modifiers, offset,
					length, offset, length, ((VariableReference) left)
							.getName(), null, null, null);
		}
		return visitGeneral(assignment);
	}

	public boolean endvisit(Assignment assignment) throws Exception {
		endvisitGeneral(assignment);
		return true;
	}

	public boolean visit(GlobalStatement s) throws Exception {
		if (!declarations.empty()
				&& declarations.peek() instanceof MethodDeclaration) {
			for (Expression var : s.getVariables()) {
				if (var instanceof ReferenceExpression) {
					var = ((ReferenceExpression) var).getVariable();
				}
				if (var instanceof SimpleReference) {
					methodGlobalVars.peek().add(
							((SimpleReference) var).getName());
				}
			}
		}
		return visitGeneral(s);
	}

	public boolean visit(TypeReference reference) throws Exception {
		requestor.addReference(IModelElement.TYPE, reference.sourceStart(),
				reference.sourceEnd() - reference.sourceStart(), reference
						.getName(), null, null);
		return visitGeneral(reference);
	}

	public boolean visit(Statement node) throws Exception {
		Class<?> statementClass = node.getClass();
		if (statementClass.equals(PHPFieldDeclaration.class)) {
			return visit((PHPFieldDeclaration) node);
		}
		if (statementClass.equals(FieldDeclaration.class)) {
			return visit((FieldDeclaration) node);
		}
		if (statementClass.equals(ConstantDeclaration.class)) {
			return visit((ConstantDeclaration) node);
		}
		if (statementClass.equals(GlobalStatement.class)) {
			return visit((GlobalStatement) node);
		}
		return visitGeneral(node);
	}

	public boolean endvisit(Statement node) throws Exception {
		Class<?> statementClass = node.getClass();
		if (statementClass.equals(PHPFieldDeclaration.class)) {
			return endvisit((PHPFieldDeclaration) node);
		}
		if (statementClass.equals(FieldDeclaration.class)) {
			return endvisit((FieldDeclaration) node);
		}
		if (statementClass.equals(ConstantDeclaration.class)) {
			return endvisit((ConstantDeclaration) node);
		}
		endvisitGeneral(node);
		return true;
	}

	public boolean visit(Expression node) throws Exception {
		Class<?> expressionClass = node.getClass();
		if (expressionClass.equals(Assignment.class)) {
			return visit((Assignment) node);
		}
		if (expressionClass.equals(TypeReference.class)) {
			return visit((TypeReference) node);
		}
		if (expressionClass.equals(Include.class)) {
			return visit((Include) node);
		}
		if (expressionClass.equals(PHPCallExpression.class)) {
			return visit((PHPCallExpression) node);
		}
		return visitGeneral(node);
	}

	public boolean endvisit(Expression node) throws Exception {
		Class<?> expressionClass = node.getClass();
		if (expressionClass.equals(Assignment.class)) {
			return endvisit((Assignment) node);
		}
		endvisitGeneral(node);
		return true;
	}

	public boolean endvisit(ModuleDeclaration declaration) throws Exception {
		while (deferredDeclarations != null && !deferredDeclarations.isEmpty()) {
			final ASTNode[] declarations = deferredDeclarations
					.toArray(new ASTNode[deferredDeclarations.size()]);
			deferredDeclarations.clear();

			for (ASTNode deferred : declarations) {
				deferred.traverse(this);
			}
		}
		endvisitGeneral(declaration);
		return true;
	}

	public void endvisitGeneral(ASTNode node) throws Exception {
		fNodes.pop();
	}

	public boolean visitGeneral(ASTNode node) throws Exception {
		fNodes.push(node);
		return true;
	}
}
