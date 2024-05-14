/**
 * kilauea
 * 
 * Published under Apache-2.0 license (https://github.com/CodeWeazle/kilauea/blob/main/LICENSE)
 * 
 * Code: https://github.com/CodeWeazle/kilauea
 * 
 * @author CodeWeazle (2023)
 * 
 * Filename: ElementInfo.java
 */
package net.magiccode.kilauea.generator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.squareup.javapoet.ClassName;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * ElementInfo is a data transfer object which keeps information about the arguments of the 
 * annotation and some additional information, such as the fields of the annotated class etc.
 * This class is referred to in the generator as <i>annotationInfo</i>.
 */
@Builder
@Getter @Accessors(fluent = true)
@Setter
public class ElementInfo {
	
	/**
	 * type of generator to use for class generation.
	 * These can be POJO,JSON or XML.
	 */
	private GeneratorType type;
	
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
	 * if set to true (default), setter methods will return <i>this</i>.
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
	 * Allows to specify the value for the @see @JsonInclude annotation of the 
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
	 * additional fields to be created for the generated class
	 */
	@Builder.Default
	private Map<String, TypeMirror> additionalFields = new HashMap<>();
	
	/**
	 * supuerclass for generated classes
	 */
	private ClassName superclass;
	
	private boolean useLombok;
	
	private String datePattern;

	private String dateTimePattern;
	
	// xml namespace
	private String xmlns;
	
	/**
	 * add an interface specification from a ClassName.
	 * 
	 * @param className representation of the Interface to be added.
	 */
	public void addInterface(ClassName className) {
		interfaces.add(className);
	}
	
	/**
	 * checks if interface has already been added
	 * @param className
	 * @return true if the interface is already contained.
	 */
	public boolean hasInterface(ClassName className) {
		return interfaces().stream().filter(intf-> intf.canonicalName().equals(className.canonicalName())).count()>0;
	}

	private boolean annotatedClassHasPublicConstructor;
}
