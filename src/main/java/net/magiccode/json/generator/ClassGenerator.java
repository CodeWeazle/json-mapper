/**
 * json-mapper
 * 
 * Published under Apache-2.0 license (https://github.com/CodeWeazle/json-mapper/blob/main/LICENSE)
 * 
 * Code: https://github.com/CodeWeazle/json-mapper
 * 
 * @author CodeWeazle (2023)
 * 
 * Filename: ClassGenerator.java
 */
package net.magiccode.json.generator;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import net.magiccode.json.annotation.Mapped;
import net.magiccode.json.util.StringUtil;

/**
 * class generator interface for JsonMapper with some helpful 
 * default implementations
 */
public interface ClassGenerator {

	/**
	 * initiate the generation process.
	 * 
	 * @throws IOException if file cannot be written
	 */
	public void generate() throws IOException;
	
	/**
	 * Generate the class code with given fields and methods
	 * 
	 * @param annotationInfo - information about the annotation arguments
	 * @param className - class name of the class to be created
	 * @param packageName - the package the class to be created shall be located in.
	 * @param fields - list of fields to be created
	 * @param methods - maps with MethodSpec definitions for methods to be created
	 * @return typeSpec - object containing the newly generated class
	 */
	public TypeSpec generateClass(ElementInfo annotationInfo, 
								   String className, 
								   String packageName,
								   List<FieldSpec> fields, 
								   Map<String, MethodSpec> methods);	
	

	/**
	 * create no-args constructor
	 * 
	 * @param methods - maps with MethodSpec definitions for methods to be created
	 */
	default void createNoArgsConstructor(Map<String, MethodSpec> methods) {
		methods.put("_constructor", MethodSpec.constructorBuilder()
							  .addModifiers(Modifier.PUBLIC)
							  .build());
	}
	
	/**
	 * create setter method
	 * 
	 * @param field - the VariableElement or the field the setter is to be created for. 
	 * @param annotationInfo - information about the arguments of the <i>@JSONMapped</i> annotation
	 * @param fieldTypeName - {@code TypeName} of the class for the field for which the setter method is to be created.
	 * @param methods - maps with MethodSpec definitions for methods to be created
	 */
	default void createSetterMethodSpec(VariableElement field, 
											  ElementInfo annotationInfo,
											  TypeName fieldTypeName,
											  final Map<String, MethodSpec> methods) {
		// create setter method
		String className = annotationInfo.prefix() + annotationInfo.className();
		String packageName = annotationInfo.packageName();
		String setterName = generateSetterName(annotationInfo, field.getSimpleName().toString());
		TypeMirror type = field.asType();
		        
		TypeName fieldType = checkFieldTypeForCollections(annotationInfo, type, fieldTypeName);
		
		MethodSpec.Builder setterBuilder = MethodSpec
				.methodBuilder(setterName)
				.addModifiers(Modifier.PUBLIC)
				.addParameter(fieldType, field.getSimpleName().toString(), new Modifier[0]);

		// DeclaredType
		if (type.getKind() == TypeKind.DECLARED) {
			
			List<TypeName> typeArguments = obtainTypeArguments(type);
			
			// todo: add recursion
			// obtain type arguments
			if (typeArguments != null && typeArguments.size()>0) {
				final StringBuilder typeArgString = new StringBuilder();
				typeArguments.stream().forEach(argument -> {
					String argString = argument.toString();
                    if (typeArgString.length() > 0) {
                    	 typeArgString.append(",");
                    }
                    
                    Element argumentElement = getElementUtils().getTypeElement(argString);
					boolean argumentIsMapped = fieldIsMapped(argumentElement);
                    if (argumentIsMapped) {
						if (argumentElement instanceof TypeElement) {
							ClassName argumentClassName = ClassName.get((TypeElement) argumentElement); 
							String fcName = annotationInfo.prefix()+argumentClassName.simpleName();
							ClassName mappedFieldClassName = ClassName.get(generatePackageName(argumentClassName, annotationInfo), fcName);
							argString = mappedFieldClassName.canonicalName();
						}
                    }
                    typeArgString.append(argString);                    
				});
				String typeArgs = "<"+typeArgString.toString()+">";

				TypeMirror collectionType = getElementUtils().getTypeElement("java.util.Collection").asType();
				TypeMirror mapType = getElementUtils().getTypeElement("java.util.Map").asType();
				TypeMirror setType = getElementUtils().getTypeElement("java.util.Set").asType();
				// List
				if (type != null && 
					getTypeUtils().isAssignable(
						getTypeUtils().erasure(type), 
						getTypeUtils().erasure(collectionType ))) {
						setterBuilder.addStatement("this.$L = new $T"+typeArgs+"()", field.getSimpleName().toString(), ArrayList.class)
									 .addStatement("this.$L.addAll($L)", field.getSimpleName().toString(),field.getSimpleName().toString());						
				// Set
				} else if (type != null &&
					getTypeUtils().isAssignable(
							getTypeUtils().erasure(type), 
							getTypeUtils().erasure(setType ))) {
						setterBuilder.addStatement("this.$L = new $T"+typeArgs+"()", field.getSimpleName().toString(), HashSet.class)
									 .addStatement("this.$L.addAll($L)", field.getSimpleName().toString(),field.getSimpleName().toString());
				// Map
				} else if (type != null && getTypeUtils().isAssignable(
												getTypeUtils().erasure(type), 
												getTypeUtils().erasure(mapType ))) {
						 setterBuilder.addStatement("this.$L = new $T"+typeArgs+"()", field.getSimpleName().toString(), HashMap.class)
					 				  .addStatement("this.$L.putAll($L)", field.getSimpleName().toString(),field.getSimpleName().toString());
				}
			} else {
				setterBuilder.addStatement("this.$L = $L", field.getSimpleName().toString(), field.getSimpleName().toString());
			} 
		// ArrayType
		} else if (type.getKind() == TypeKind.ARRAY) {
			 setterBuilder.addStatement("this.$L = $L.clone()", field.getSimpleName().toString(), 
					 										    field.getSimpleName().toString());
		} else if (type.getKind() != TypeKind.PACKAGE &&
				   type.getKind() != TypeKind.MODULE &&
				   type.getKind() != TypeKind.ERROR &&
				   type.getKind() != TypeKind.EXECUTABLE &&
				   type.getKind() != TypeKind.UNION &&
				   type.getKind() != TypeKind.NULL) {
			setterBuilder.addStatement("this.$L = $L", field.getSimpleName().toString(), 
					    							   field.getSimpleName().toString());
		}
		if (annotationInfo.chainedSetters()) {
			setterBuilder.addStatement("return this")
						 .returns(ClassName.get(packageName, className));
		}
		methods.put(setterName,setterBuilder.build());
	}

	/**
 	 * create getter method
 	 * 
	 * @param field - the VariableElement or the field the setter is to be created for. 
	 * @param annotationInfo - information about the arguments of the <i>@JSONMapped</i> annotation
	 * @param fieldTypeName - {@code TypeName} of the class for the field for which the getter method is to be created.
	 * @param methods - maps with MethodSpec definitions for methods to be created
	 */
	default void createGetterMethodSpec(final VariableElement field, 
										final ElementInfo annotationInfo,
										final TypeName fieldTypeName,
										final Map<String, MethodSpec> methods) {
		// create getter method
		String getterName = generateGetterName(annotationInfo, field.getSimpleName().toString(), fieldTypeName.toString().equals(Boolean.class.getName()));
		String fieldName = field.getSimpleName().toString();
		
		TypeMirror type = field.asType();
		TypeName fieldType = checkFieldTypeForCollections(annotationInfo, type, fieldTypeName);
		
		MethodSpec getter = MethodSpec
				.methodBuilder(getterName)
				.addModifiers(Modifier.PUBLIC)
				.addStatement("return "+fieldName)
				.returns(fieldType)
				.build();
		methods.put(getterName, getter);
	}

	/**
	 * create field
	 * 
	 * @param field - VariableElement representation of field to be created 
	 * @param annotationInfo - information about the arguments of the <i>@JSONMapped</i> annotation
	 * @param fieldClass - TypeName for class field shall be created in.
	 * @param fieldIsMapped - indicates whether or not the given field is annotated
	 * 						  
	 * @return field specification for the create field.
	 */
	default FieldSpec createFieldSpec(VariableElement field, ElementInfo annotationInfo, TypeName fieldClass, boolean fieldIsMapped) {
		FieldSpec fieldspec = FieldSpec.builder(fieldClass, field.getSimpleName().toString(), Modifier.PRIVATE).build();
		return fieldspec;
	}

	/**
	 * generate a name for the field's getter method according to the specified method.
	 * (fluent or not)
	 * 
	 * @param annotationInfo - information about the annotation arguments
	 * @param fieldName - name of the field to create the setter's name for
	 * @param isBoolean - flag to decide whether to use 'is' or 'get'
	 * @return the generated name for the getter
	 */
	default String generateGetterName(ElementInfo annotationInfo, String fieldName, Boolean isBoolean) {
		if (annotationInfo.fluentAccessors()) {
			return StringUtil.uncapitalise(fieldName);
		} else {			
			return ((isBoolean!=null&& isBoolean.equals(Boolean.TRUE))?"is":"get")+StringUtil.capitalise(fieldName);
		}
	}
	
	/**
	 * generate a name for the field's setter method according to the specified method.
	 * (fluent or not)
	 * 
	 * @param annotationInfo - information about the annotation arguments
	 * @param fieldName - name of the field to create the setter's name for
	 * @return the generated setter name
	 */
	default String generateSetterName(ElementInfo annotationInfo, String fieldName) {
		if (annotationInfo.fluentAccessors()) {
			return StringUtil.uncapitalise(fieldName);
		} else {			
			return "set"+StringUtil.capitalise(fieldName);
		}
	}
	
	
	/**
	 * generate toString method
	 * 
	 * @param annotationInfo - information about the annotation arguments
	 * @param methods - maps with MethodSpec definitions for methods to be created
	 */
	default void createToString(ElementInfo annotationInfo, Map<String, MethodSpec> methods) {
		// create toSTring method		
		MethodSpec.Builder toStringBuilder = MethodSpec.methodBuilder("toString")
				.addModifiers(Modifier.PUBLIC)
				.addStatement("$T stringRep = this.getClass().getName()+ \"(\"", String.class);
		annotationInfo.fields().stream()
					  .filter(field -> ! isMethodFinalPrivateStatic(field))
					  .forEach(field -> {
				String fieldName = field.getSimpleName().toString();
				String statement = "stringRep += \"$L=\"+$L";
				if(field != annotationInfo.fields().get(annotationInfo.fields().size() - 1))
					statement += "+\", \""; 
				toStringBuilder.addStatement(statement, fieldName, fieldName);
		});
		toStringBuilder.addStatement("stringRep += \")\"")
						.addStatement("return stringRep")
						.addJavadoc(CodeBlock
							    .builder()
							    .add("All fields as a comma-separated list.\n")
							    .build());
		
		toStringBuilder.returns(ClassName.get(String.class));
		methods.put("toString", toStringBuilder.build());
	}

	/**
	 * check if field is declared as
	 * final static private
	 * 
	 * @param field -  the @see VariableElement to be checked
	 * @return true if the given field is private, final and static
	 */
	default boolean isMethodFinalPrivateStatic(VariableElement field) {
		return (field.getModifiers().contains(Modifier.FINAL) &&
				field.getModifiers().contains(Modifier.PRIVATE) &&
				field.getModifiers().contains(Modifier.STATIC));
	}
	
	/**
	 * Checks for annotation on provided field element.
	 * 
	 * @param field - Element for the field
	 * @param annotationClazz - Class of the annotation
	 * @param type - the {@code GenerationType} enum value which specifies the type
	 * 		  that needs to be checked on the {@code Mapped} annotation	 
	 * @return true if present
	 */
	default boolean fieldIsAnnotedWith(final Element field,
									   Class<?> annotationClazz,
									   GeneratorType type) {
		TypeMirror fieldType = field.asType();
		TypeElement typeElement = getElementUtils().getTypeElement(ClassName.get(fieldType).toString());		
		return typeIsAnnotatedWith(typeElement, annotationClazz, type);
	}

	/**
	 * checks if the given {@code TypeElement} with the given  {@code Annotation} 
	 * 
	 * @param typeElement - the {@code TypeElement} to check for the annotation 
	 * @param annotationClazz - the {@code Class} of the annotation
	 * @param type - {code GeneratorType} defining the type of generator used (POJO,JSON,XML)
	 * @return boolean which indicates whether or not the given typeElement is annotated with the given class.
	 */
	default boolean typeIsAnnotatedWith(final TypeElement typeElement, 
										final Class<?> annotationClazz,
										GeneratorType type) {

		boolean isAnnotated = false;
		if (annotationClazz.isAnnotation()) {			
			isAnnotated = 
				(typeElement != null &&
				Arrays.asList(typeElement.getAnnotationsByType(  (Class<? extends Annotation>) annotationClazz)  ).stream()
				.anyMatch(annotation ->  ((Mapped) annotation).type().equals(type)));
		}
		return isAnnotated;
							
	}
	
	/** 
	 * checks the given if {@code TypeName} belongs to some kind of  {@code Collection},  {@code Set} or  {@code Map}
	 * and returns the  {@code ParameterizedTypeName} of the field.
	 *  
	 * @param annotationInfo - information about the annotation arguments
	 * @param type - {@code TypeMirror} of the the field
	 * @param fieldType - the {@code TypeName} of the field
	 * @return the {@code ParameterizedTypeName} of the field
	 */
	default TypeName checkFieldTypeForCollections(ElementInfo annotationInfo, TypeMirror type, TypeName fieldType) {
		if (type.getKind() == TypeKind.DECLARED) {			
			List<TypeName> sourceTypeArguments = obtainTypeArguments(type);
			// obtain type arguments
			List<TypeName> destinationTypeArguments = collectTypes(annotationInfo, sourceTypeArguments);
			// get {@codd TypeMirror} of types for comparison.
			TypeMirror collectionType = getElementUtils().getTypeElement("java.util.Collection").asType();
			TypeMirror mapType = getElementUtils().getTypeElement("java.util.Map").asType();
			TypeMirror setType = getElementUtils().getTypeElement("java.util.Set").asType();
			// List
			if (type != null && 
				getTypeUtils().isAssignable(
					getTypeUtils().erasure(type), 
					getTypeUtils().erasure(collectionType ))) {
					fieldType = ParameterizedTypeName.get(ClassName.get(List.class), destinationTypeArguments.get(0));
			// Set
			} else if (type != null &&
				getTypeUtils().isAssignable(
						getTypeUtils().erasure(type), 
						getTypeUtils().erasure(setType ))) {
				fieldType = ParameterizedTypeName.get(ClassName.get(Set.class), destinationTypeArguments.get(0));
			// Map
			} else if (type != null && getTypeUtils().isAssignable(
											getTypeUtils().erasure(type), 
											getTypeUtils().erasure(mapType ))) {
				fieldType = ParameterizedTypeName.get(ClassName.get(Map.class), destinationTypeArguments.get(0), destinationTypeArguments.get(1));
			}
		}
		return fieldType;
	}

	/**
	 * get type arguments from given type
	 * 
	 * @param type the {@code TypeMirror} or the Element to be checked
	 * @return a {@code List} of {@code TypeName} instances containing the type arguments of the given type. Empty if none are present. 
	 */
	default List<TypeName> obtainTypeArguments(TypeMirror type) {
		List<? extends TypeMirror> typeArgumentMirrors = ((DeclaredType) type).getTypeArguments();
		List<TypeName> typeArguments = new ArrayList<>(typeArgumentMirrors.size());
		if (typeArgumentMirrors.size()>0) {
			typeArguments.addAll(typeArgumentMirrors.stream().map(arg -> TypeName.get(arg)).collect(Collectors.toList()));
		}
		return typeArguments;
	}

	/**
	 * Collect types of a parametrized field and return mapped type
	 * if any of them is annotated with with the mapping annotation itself.
	 * 
	 * @param annotationInfo - information about the annotation arguments
	 * @param typeArguments  - {@code List} of {@code TypeName} instances containing the (source) type arguments 
	 * @return List of collected {@code TypeName} instances containing the (possibly mapped) types.
	 */
	public List<TypeName> collectTypes(ElementInfo annotationInfo, 
			 						   List<TypeName> typeArguments);

	/**
	 * generates the package name base on the given annotation arguments
	 * 
	 * @param key {@code ClassName} instance of the class to generate the package name from 
	 * @param annotationInfo - {@code ElementInfo} instance to retrieve the given sub-package name from. 
	 * @return package name considering the given package and sub-package name in the annotation arguments.  
	 */
	default String generatePackageName(ClassName key, ElementInfo annotationInfo) {
		String packageName = annotationInfo.packageName();
		if (StringUtil.isBlank(packageName)) {
			packageName = key.packageName();
			if (StringUtil.isNotBlank(annotationInfo.subpackageName())) {
				packageName += "." + annotationInfo.subpackageName();
			}
		}
		return packageName;
	}

	/**
	 * allow access to the {@code Types} provided to the {@code ClassGenerator} sub-classes constructor
	 *  
	 * @return {@code Types} provided to the {@code ClassGenerator} sub-classes constructor
	 */
	public Types getTypeUtils();
	
	/**
	 * allow access to the {@code Elements} provided to the {@code ClassGenerator} sub-classes constructor
	 *  
	 * @return {@code Elements} provided to the {@code ClassGenerator} sub-classes constructor
	 */
	public Elements getElementUtils();
	
	/**
	 * return flag to indicate whether current field is mapped or not.
	 * 
	 * @param field which is to be checked whether it is of an annotated class or not.
	 * @return indication whether or not the given typeElement is annotated.
	 */
	public boolean fieldIsMapped(final Element field);

	/**
	 * return flag to indicate whether type argument is mapped or not.
	 * 
	 * @param typeElement the typeElement to check
	 * @return indication whether or not the given typeElement is annotated.
	 */
	public boolean typeIsMapped(final TypeElement typeElement);

	
}
