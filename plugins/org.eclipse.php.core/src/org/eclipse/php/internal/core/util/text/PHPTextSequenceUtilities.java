/*******************************************************************************
 * Copyright (c) 2006 Zend Corporation and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Zend and IBM - Initial implementation
 *******************************************************************************/
package org.eclipse.php.internal.core.util.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.php.internal.core.documentModel.parser.PHPRegionContext;
import org.eclipse.php.internal.core.documentModel.parser.regions.IPhpScriptRegion;
import org.eclipse.php.internal.core.documentModel.parser.regions.PHPRegionTypes;
import org.eclipse.php.internal.core.documentModel.partitioner.PHPPartitionTypes;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegionCollection;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegionContainer;

public class PHPTextSequenceUtilities {

	private static final Pattern COMMENT_START_PATTERN = Pattern.compile("(/[*])|(//)"); //$NON-NLS-1$
	private static final Pattern COMMENT_END_PATTERN = Pattern.compile("[*]/"); //$NON-NLS-1$
	private static final String START_COMMENT = "/*"; //$NON-NLS-1$
	//	private static final String END_COMMENT = "*/";
	private static final char END_LINE = '\n';
	private static final Pattern FUNCTION_PATTERN = Pattern.compile("function\\s", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$
	private static final Pattern CLASS_PATTERN = Pattern.compile("(class|interface)\\s", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$

	private PHPTextSequenceUtilities() {
	}

	/**
	 * This function returns statement text depending on the current offset.
	 * It searches backwards until it finds ';', '{' or '}'.
	 *
	 * @param offset The absolute offset in the document
	 * @param sdRegion Structured document region of the offset
	 * @param removeComments Flag determining whether to remove comments in the resulted text sequence
	 *
	 * @return text sequence of the statement
	 */
	public static TextSequence getStatement(int offset, IStructuredDocumentRegion sdRegion, boolean removeComments) {
		int documentOffset = offset;
		if (documentOffset == sdRegion.getEndOffset()) {
			documentOffset -= 1;
		}
		ITextRegion tRegion = sdRegion.getRegionAtCharacterOffset(documentOffset);

		ITextRegionCollection container = sdRegion;

		if (tRegion instanceof ITextRegionContainer) {
			container = (ITextRegionContainer) tRegion;
			tRegion = container.getRegionAtCharacterOffset(offset);
		}
		if (tRegion != null && tRegion.getType() == PHPRegionContext.PHP_CLOSE) {
			tRegion = container.getRegionAtCharacterOffset(container.getStartOffset() + tRegion.getStart() - 1);
		}

		// This text region must be of type PhpScriptRegion:
		if (tRegion != null && tRegion.getType() == PHPRegionContext.PHP_CONTENT) {
			IPhpScriptRegion phpScriptRegion = (IPhpScriptRegion) tRegion;

			try {
				//	Set default starting position to the beginning of the PhpScriptRegion:
				int startOffset = container.getStartOffset() + phpScriptRegion.getStart();

				// Now, search backwards for the statement start (in this PhpScriptRegion):
				ITextRegion startTokenRegion;
				if (documentOffset == startOffset) {
					startTokenRegion = phpScriptRegion.getPhpToken(0);
				} else {
					startTokenRegion = phpScriptRegion.getPhpToken(documentOffset - startOffset - 1);
				}
				while (true) {
					// If statement start is at the beginning of the PHP script region:
					if (startTokenRegion.getStart() == 0) {
						break;
					}
					if (startTokenRegion.getType() == PHPRegionTypes.PHP_CURLY_CLOSE || startTokenRegion.getType() == PHPRegionTypes.PHP_CURLY_OPEN || startTokenRegion.getType() == PHPRegionTypes.PHP_SEMICOLON) {
						// Calculate starting position of the statement (it should go right after this startTokenRegion):
						startOffset += startTokenRegion.getEnd();
						break;
					}
					startTokenRegion = phpScriptRegion.getPhpToken(startTokenRegion.getStart() - 1);
				}

				TextSequence textSequence = TextSequenceUtilities.createTextSequence(sdRegion, startOffset, offset - startOffset);

				// remove comments
				// TODO if the text sequence is large just ignore it, should be fixed.
				if (removeComments && textSequence.length() < 1000) {
					textSequence = removeComments(textSequence);
				}

				// remove spaces from start.
				textSequence = textSequence.subTextSequence(readForwardSpaces(textSequence, 0), textSequence.length());
				return textSequence;

			} catch (BadLocationException e) {
			}
		}

		return null;
	}

	private static TextSequence removeComments(TextSequence textSequence) {
		FIND_COMMENT_START: while (true) {
			int commentStartPosition = getCommentStartIndex(textSequence);
			if (commentStartPosition > -1) {
				String startCommentString = textSequence.subSequence(commentStartPosition, commentStartPosition + 2).toString();
				if (startCommentString.equals(START_COMMENT)) {
					// we are inside comment.
					Matcher commentEndMatcher = COMMENT_END_PATTERN.matcher(textSequence);
					boolean foundEnd = commentEndMatcher.find(commentStartPosition);
					if (foundEnd) {
						int commentEndPosition = commentEndMatcher.end();
						textSequence = textSequence.cutTextSequence(commentStartPosition, commentEndPosition);
						continue;
					}
				} else {
					// we are inside line comment.
					int commentEndPosition = commentStartPosition + 2;
					for (; commentEndPosition < textSequence.length(); commentEndPosition++) {
						if (textSequence.charAt(commentEndPosition) == END_LINE) {
							textSequence = textSequence.cutTextSequence(commentStartPosition, commentEndPosition);
							continue FIND_COMMENT_START;
						}
					}
				}
			}
			return textSequence;
		}
	}

	private static int getCommentStartIndex(TextSequence textSequence) {
		Matcher commentStartMatcher = COMMENT_START_PATTERN.matcher(textSequence);
		int start = 0;
		while (commentStartMatcher.find(start)) {
			String currentType = TextSequenceUtilities.getType(textSequence, commentStartMatcher.start());
			if (PHPPartitionTypes.isPHPCommentState(currentType) && !PHPPartitionTypes.isPHPQuotesState(currentType)) {
				return commentStartMatcher.start();
			}
			start = commentStartMatcher.start() + 2;
		}
		return -1;
	}

	/**
	 * Checks if we are inside function declaration statement.
	 * If yes the start offset of the function, otherwise returns -1.
	 */
	public static int isInFunctionDeclaration(TextSequence textSequence) {
		Matcher matcher = FUNCTION_PATTERN.matcher(textSequence);
		// search for the 'function' word.
		while (matcher.find()) {
			// verify char before 'function' word.
			int functionStart = matcher.start();
			if (functionStart != 0 && Character.isJavaIdentifierStart(textSequence.charAt(functionStart - 1))) {
				continue;
			}

			// verfy state
			String type = TextSequenceUtilities.getType(textSequence, functionStart + 1);
			if (PHPPartitionTypes.isPHPRegularState(type)) {
				// verify the function is not closed.
				int offset;
				for (offset = matcher.end(); offset < textSequence.length(); offset++) {
					if (textSequence.charAt(offset) == ')') {
						// verify state
						type = TextSequenceUtilities.getType(textSequence, offset);
						if (PHPPartitionTypes.isPHPRegularState(type)) {
							break;
						}
					}
				}
				if (offset == textSequence.length()) {
					return functionStart;
				}
			}
		}
		return -1;
	}

	public static int isInClassDeclaration(TextSequence textSequence) {
		Matcher matcher = CLASS_PATTERN.matcher(textSequence);
		// search for the 'class' or 'interface words.
		while (matcher.find()) {
			// verify char before start.
			int startOffset = matcher.start();
			if (startOffset != 0 && Character.isJavaIdentifierStart(textSequence.charAt(startOffset - 1))) {
				continue;
			}
			// verify state
			String type = TextSequenceUtilities.getType(textSequence, startOffset + 1);
			if (PHPPartitionTypes.isPHPRegularState(type)) {
				int endOffset = matcher.end();
				// verify the class is not closed.
				int offset;
				for (offset = endOffset; offset < textSequence.length(); offset++) {
					if (textSequence.charAt(offset) == '}') {
						// verify state
						type = TextSequenceUtilities.getType(textSequence, offset);
						if (PHPPartitionTypes.isPHPRegularState(type)) {
							break;
						}
					}
				}
				if (offset == textSequence.length()) {
					return endOffset;
				}
			}
		}
		return -1;
	}

	public static int readIdentifierStartIndex(TextSequence textSequence, int startPosition, boolean includeDolar) {
		while (startPosition > 0) {
			char ch = textSequence.charAt(startPosition - 1);
			if (!Character.isLetterOrDigit(ch) && ch != '_') {
				break;
			}
			startPosition--;
		}
		if (includeDolar && startPosition > 0 && textSequence.charAt(startPosition - 1) == '$') {
			startPosition--;
		}
		return startPosition;
	}

	public static int readIdentifierEndIndex(TextSequence textSequence, int startPosition, boolean includeDolar) {
		int length = textSequence.length();
		if (includeDolar && startPosition < length && textSequence.charAt(startPosition) == '$') {
			startPosition++;
		}
		while (startPosition < length) {
			char ch = textSequence.charAt(startPosition);
			if (!Character.isLetterOrDigit(ch) && ch != '_') {
				break;
			}
			startPosition++;
		}
		return startPosition;
	}

	public static int readBackwardSpaces(TextSequence textSequence, int startPosition) {
		int rv = startPosition;
		for (; rv > 0; rv--) {
			if (!Character.isWhitespace(textSequence.charAt(rv - 1))) {
				break;
			}
		}
		return rv;
	}

	public static int readForwardSpaces(TextSequence textSequence, int startPosition) {
		int rv = startPosition;
		for (; rv < textSequence.length(); rv++) {
			if (!Character.isWhitespace(textSequence.charAt(rv))) {
				break;
			}
		}
		return rv;
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////////////////////

	public static int getPrivousTriggerIndex(TextSequence textSequence, int startPosition) {
		int rv = startPosition;
		int bracketsNum = 0;
		char inStringMode = 0;
		boolean inWhiteSpaceBeforeLiteral = false;
		boolean inLiteral = false;
		for (; rv > 0; rv--) {
			char currChar = textSequence.charAt(rv - 1);
			if (currChar == '\'' || currChar == '"') {
				inStringMode = inStringMode == 0 ? currChar : inStringMode == currChar ? 0 : inStringMode;
			}
			if (inStringMode != 0) {
				continue;
			}

			// The next block solves bug #205034:
			// store state for whitespace before literals and if another literal comes before it - return 'not found'
			if (Character.isLetterOrDigit(currChar) || currChar == '$') {
				if (inWhiteSpaceBeforeLiteral) {
					return -1;
				}
				inLiteral = true;
			} else {
				if (inLiteral && Character.isWhitespace(currChar)) {
					inWhiteSpaceBeforeLiteral = true;
				}
				if (!Character.isWhitespace(currChar)) {
					inWhiteSpaceBeforeLiteral = false;
				}
				inLiteral = false;
			}

			if (!Character.isLetterOrDigit(currChar) && currChar != '_' && currChar != '$' && !Character.isWhitespace(currChar)) {
				switch (currChar) {
					case '(':
					case '[':
					case '{':
						bracketsNum--;
						if (bracketsNum < 0) {
							return -1;
						}
						break;
					case ')':
					case ']':
					case '}':
						bracketsNum++;
						break;
					case ':':
						if (bracketsNum == 0 && rv >= 2) {
							if (textSequence.charAt(rv - 2) == ':') {
								return rv - 2;
							} else {
								return -1;
							}
						}
						break;
					case '>':
						if (bracketsNum == 0 && rv >= 2) {
							if (textSequence.charAt(rv - 2) == '-') {
								return rv - 2;
							}
						}
						break;
					default:
						if (bracketsNum == 0) {
							return -1;
						}
				}
			}
		}
		return -1;
	}

	public static int readIdentifierListStartIndex(TextSequence textSequence, int endPosition) {
		int startPosition = endPosition;
		int listStartPosition = startPosition;
		boolean beforeWhitespace = false;
		boolean beforeComma = false;
		while (startPosition > 0) {
			final char ch = textSequence.charAt(startPosition - 1);
			if (Character.isLetterOrDigit(ch) || ch == '_') {
				if (beforeWhitespace) {
					// identifiers delimited by a whitespace are not a list:
					return --listStartPosition;
				}
				listStartPosition = startPosition;
				beforeComma = false;
			} else if (ch == ',') {
				if (beforeComma) {
					// only one comma may delimit a list
					return endPosition;
				}
				beforeComma = true;
				beforeWhitespace = false;
			} else if (Character.isWhitespace(ch) && !beforeComma) {
				beforeWhitespace = true;
			} else {
				return --listStartPosition;
			}
			startPosition--;
		}
		return listStartPosition;
	}

}
