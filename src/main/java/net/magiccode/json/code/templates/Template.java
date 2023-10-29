/**
 * 
 */
package net.magiccode.json.code.templates;

/**
 * 
 */
public interface Template {

	default StringBuilder startTemplate(String objectName,
										String visibility,
										StringBuilder objectBuffer) {return objectBuffer;};
	
	default StringBuilder endTemplate(StringBuilder objectBuffer) {return objectBuffer;};

	default String adjustObjectName(String rawObjectName) {return rawObjectName;};
}
