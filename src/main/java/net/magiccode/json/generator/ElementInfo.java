/**
 * 
 */
package net.magiccode.json.generator;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.squareup.javapoet.ClassName;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 
 */
@Builder
@Getter @Accessors(fluent = true)
@Setter
public class ElementInfo {
	
	/** 
	 * store name of annotated class
	 */
	private String className;
	
	/**
	 * optional package name for generated class
	 */
	private String packageName;
	
	/**
	 * When package name is not given, a sub-package name
	 * can be specified. This is then appended to the package
	 * of the annotated class.
	 */
	private String subpackageName;
	
	/**
	 * (optional) prefix for generated class.
	 * Defaults to "JSON"
	 */
	private String prefix;
	
	/**
	 * The element within the AST that represents the annotated class
	 */
	private TypeElement element;
	
	/**
	 * if set to true (default), setter methods will return <7>this</i>.
	 */
	private boolean chainedSetters;
	
	/**
	 * If true, accessors will be generated without is/set/get.
	 */
	private boolean fluentAccessors;
	
	
	/**
	 * defines whether or not fields from super-classes are inherited and also 
	 * generated. 
	 */
	private boolean inheritFields;
	
	/**
	 * Allows to specify the value for the <link>@JsonInclude</link> annotation of the 
	 * generated class. Defaults to Include.ALWAYS,
	 * Possible values are
	 * ALWAYS,NON_NULL,NON_ABSENT,NON_EMPTY,NON_DEFAULT,CUSTOM,USE_DEFAULTS
	 */
	private Include jsonInclude;
	
	/**
	 * List of fields in this element
	 */
	private List<VariableElement> fields;
	
	/**
	 * interfaces for generated classes
	 */
	@Builder.Default
	private List<ClassName> interfaces = new ArrayList();
	
	/**
	 * supuerclass for generated classes
	 */
	private ClassName superclass;
	
	// experimental
	private boolean useLombok;
	
	
	public void addInterface(ClassName className) {
		interfaces.add(className);
	}
	
	public boolean hasInterface(ClassName className) {
		return interfaces().stream().filter(intf-> intf.canonicalName().equals(className.canonicalName())).count()>0;
	}
	
	
}
