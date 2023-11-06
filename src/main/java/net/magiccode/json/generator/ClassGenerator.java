package net.magiccode.json.generator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
//import java.lang.foreign.MemorySegment;
//import java.lang.foreign.SymbolLookup;
import java.util.List;

import javax.lang.model.element.Modifier;
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
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

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
	 * @param methods - list of methods to be created
	 * @return typeSpec - object containing the newly generated class
	 */
	public TypeSpec generateClass(ElementInfo annotationInfo, 
								   String className, 
								   String packageName,
								   List<FieldSpec> fields, 
								   List<MethodSpec> methods);	
	

	/**
	 * create no-args constructor
	 * 
	 * @param methods - list of methods to be created
	 */
	default void createNoArgsConstructor(List<MethodSpec> methods) {
		methods.add(MethodSpec.constructorBuilder()
							  .addModifiers(Modifier.PUBLIC)
							  .build());
	}
	
	/**
	 * create setter method
	 * 
	 * @param field - the VariableElement or the field the setter is to be created for. 
	 * @param annotationInfo - information about the arguments of the <i>@JSONMapped</i> annotation
	 * @return specification for setter method
	 */
	default MethodSpec createSetterMethodSpec(VariableElement field, 
											  ElementInfo annotationInfo,
											  TypeName fieldTypeName) {
		// create setter method
		String className = annotationInfo.prefix() + annotationInfo.className();
		String packageName = annotationInfo.packageName();
		String setterName = generateSetterName(annotationInfo, field.getSimpleName().toString());
		TypeMirror type = field.asType();
		        
		MethodSpec.Builder setterBuilder = MethodSpec
				.methodBuilder(setterName)
				.addModifiers(Modifier.PUBLIC)
				.addParameter(fieldTypeName, field.getSimpleName().toString(), new Modifier[0]);

		// DeclaredType
		if (type.getKind() == TypeKind.DECLARED) {
			TypeMirror mapType = getElementUtils().getTypeElement("java.util.Map").asType();
			TypeMirror setType = getElementUtils().getTypeElement("java.util.Set").asType();
			TypeMirror collectionType = getElementUtils().getTypeElement("java.util.Collection").asType();
			
			List<? extends TypeMirror> typeArguments = ((DeclaredType) type).getTypeArguments();
			// todo: add recursion
			// obtain type arguments
			if (typeArguments != null && typeArguments.size()>0) {
				final StringBuilder typeArgString = new StringBuilder();
				typeArguments.stream().forEach(argument -> {
					String argString = getTypeUtils().erasure(argument).toString();
                    if (typeArgString.length() > 0) {
                    	 typeArgString.append(",");
                    }
                    typeArgString.append(argString);                    
				});
				String typeArgs = "<"+typeArgString.toString()+">";
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
		return setterBuilder.build();
	}

	/**
	 * create getter method
	 * 
	 * @param field - the VariableElement or the field the setter is to be created for.
	 * @param annotationInfo - information about the arguments of the <i>@JSONMapped</i> annotation
	 * @return specification for getter method
	 */
	default MethodSpec createGetterMethodSpec(VariableElement field, 
											  ElementInfo annotationInfo,
											  TypeName fieldTypeName) {
		// create getter method
		String getterName = generateGetterName(annotationInfo, field.getSimpleName().toString(), fieldTypeName.toString().equals(Boolean.class.getName()));
		String fieldName = field.getSimpleName().toString();
		MethodSpec getter = MethodSpec
				.methodBuilder(getterName)
				.addModifiers(Modifier.PUBLIC)
				.addStatement("return "+fieldName)
				.returns(fieldTypeName)
				.build();
		return getter;
	}

	/**
	 * create field
	 * 
	 * @param field - VariableElement representation of field to be created 
	 * @param annotationInfo - information about the arguments of the <i>@JSONMapped</i> annotation
	 * @param fieldClass - TypeName for class field shall be created in.
	 * @param fieldIsMapped - indicates whether or not the given field is annotated
	 * 						  with {@code JSONMapped}.
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
	 * @param methods - list of methods to be created
	 */
	default void createToString(ElementInfo annotationInfo, List<MethodSpec> methods) {
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
		methods.add(toStringBuilder.build());
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
	
	public Types getTypeUtils();
	public Elements getElementUtils();
	

}
