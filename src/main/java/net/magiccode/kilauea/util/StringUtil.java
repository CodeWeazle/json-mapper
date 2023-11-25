/**
 * kilauea
 * 
 * Published under Apache-2.0 license (https://github.com/CodeWeazle/kilauea/blob/main/LICENSE)
 * 
 * Code: https://github.com/CodeWeazle/kilauea
 * 
 * @author CodeWeazle (2023)
 * 
 * Filename: StringUtil.java
 */
package net.magiccode.kilauea.util;

import java.util.Arrays;


/**
 * helpful routines for checking and modifying strings.
 * 
 */
public class StringUtil {

	/**
	 * @param stringToCheck
	 * @return
	 */
	public static boolean isBlank(String stringToCheck) {
		return stringToCheck == null || stringToCheck.isBlank();
	}

	/**
	 * @param stringToCheck
	 * @return
	 */
	public static boolean isNotBlank(String stringToCheck) {
		return !isBlank(stringToCheck);
	}

	/**
	 * finally spelt correctly ;)
	 * @param str
	 * @return
	 */
	public static String capitalise(final String str) {
		int strLen;
		if (str == null || (strLen = str.length()) == 0) {
			return str;
		}

		final char firstChar = str.charAt(0);
		final char newChar = Character.toTitleCase(firstChar);
		if (firstChar == newChar) {
			// already capitalized
			return str;
		}

		char[] newChars = new char[strLen];
		newChars[0] = newChar;
		str.getChars(1, strLen, newChars, 1);
		return String.valueOf(newChars);
	}

	/**
	 * @param str
	 * @return
	 */
	public static String uncapitalise(final String str) {
		int strLen;
		if (str == null || (strLen = str.length()) == 0) {
			return str;
		}

		final char firstChar = str.charAt(0);
		final char newChar = Character.toLowerCase(firstChar);
		if (firstChar == newChar) {
			// already uncapitalized
			return str;
		}

		char[] newChars = new char[strLen];
		newChars[0] = newChar;
		str.getChars(1, strLen, newChars, 1);
		return String.valueOf(newChars);
	}

	/**
	 * capitalise all words in a sentence.
	 * 
	 * @param inputString
	 * @return
	 */
	final public static String capitaliseAll(final String inputString) {
		String[] subStrings = inputString.split("\\s");
		final StringBuffer resultBuffer = new StringBuffer();
		Arrays.asList(subStrings).stream().forEach(subString -> {
			resultBuffer.append(StringUtil.capitalise(subString));
			if (resultBuffer.length() != inputString.length())
				resultBuffer.append(' ');
		});
		return resultBuffer.toString();
	}

	/**
	 * @param stringToCheck
	 * @return
	 */
	final public static String clearSlashes(final String stringToCheck) {
		String resultString = new String(stringToCheck);
		final String midfix = "://";
		String prefix, suffix;

		if (resultString.contains(midfix)) {
			int position = resultString.indexOf(midfix, 0);
			prefix = resultString.substring(0, position);
			suffix = resultString.substring(position + midfix.length(), resultString.length());
			resultString = unifiySlashes(prefix) + midfix + clearSlashes(suffix);
		} else {
			resultString = unifiySlashes(resultString);
		}
		return resultString;
	}

	/**
	 * @return
	 */
	final static private String unifiySlashes(String stringToCheck) {
		String resultString = new String(stringToCheck);
		while (resultString.contains("//"))
			resultString = resultString.replace("//", "/");
		return resultString;
	}

	/**
	 * @param stringToQuote
	 * @param quoteChar
	 * @return
	 */
	final public static String quote(final String stringToQuote, Character quoteChar) {
		return new StringBuffer().append(quoteChar).append(stringToQuote).append(quoteChar).toString();
	}

	/**
	 * @param stringToQuote
	 * @return
	 */
	final public static String quote(final String stringToQuote) {
		return quote(stringToQuote, '"');
	}

	/**
	 * @param str
	 * @return
	 */
	public static String camelToSnake(String str) {

		// Empty String
		String result = "";

		// Append first character(in lower case)
		// to result string
		char c = str.charAt(0);
		result = result + Character.toLowerCase(c);

		// Traverse the string from
		// ist index to last index
		for (int i = 1; i < str.length(); i++) {

			char ch = str.charAt(i);

			// Check if the character is upper case
			// then append '_' and such character
			// (in lower case) to result string
			if (Character.isUpperCase(ch)) {
				result = result + '_';
				result = result + Character.toLowerCase(ch);
			}

			// If the character is lower case then
			// add such character into result string
			else {
				result = result + ch;
			}
		}

		// return the result
		return result;
	}

}
