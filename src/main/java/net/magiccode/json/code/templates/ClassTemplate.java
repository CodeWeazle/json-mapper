/**
 * 
 */
package net.magiccode.json.code.templates;


import lombok.Builder;
import net.magiccode.json.util.StringUtil;

/**
 * 
 */
@Builder
public class ClassTemplate {
	
	final static String TMP_VISIBILITY_DEFAULT = "";
	final static String TMP_CLASS = "class ";
	final static String SPACE = " ";
	final static String JSON_PREFIX = "JSON";

	public StringBuilder startTemplate(String objectName,
									   String visibility,
									   StringBuilder objectBuffer) {
		return objectBuffer.append((StringUtil.isBlank(visibility)?TMP_VISIBILITY_DEFAULT:visibility)+TMP_CLASS+SPACE)
						   .append(adjustObjectName(objectName)).append(" {");
	}
	
	public StringBuilder endTemplate(StringBuilder objectBuffer) {
		return objectBuffer.append("}");
	}

	/**
	 * adds JSON as a prefix and uncapitalises the classname. 
	 * Example: <i>SomeEntity</i> becomes <i>JSONsomeEntity</i> 
	 *  
	 * @param rawObjectName
	 * @return the generated class name for the class to be generated
	 */
	public String adjustObjectName(String rawObjectName) {
		return JSON_PREFIX + StringUtil.uncapitalise(rawObjectName);
	}
}
