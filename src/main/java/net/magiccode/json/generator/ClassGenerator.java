package net.magiccode.json.generator;

import java.io.IOException;
import java.util.List;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

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
	 * @throws IOException
	 */
	public void generate() throws IOException;
	
	/**
	 * Generate the class code with given fields and methods
	 * 
	 * @param annotationInfo
	 * @param className
	 * @param packageName
	 * @param fields
	 * @param methods
	 * @return typeSpec object containing the newly generated class
	 */
	public TypeSpec generateClass(ElementInfo annotationInfo, 
								   String className, 
								   String packageName,
								   List<FieldSpec> fields, 
								   List<MethodSpec> methods);	
	

	/**
	 * create no-args constructor
	 * 
	 * @param packageName
	 * @param className
	 * @param methods
	 */
	default void createNoArgsConstructor(String packageName, String className, List<MethodSpec> methods) {
		methods.add(MethodSpec.constructorBuilder()
							  .addModifiers(Modifier.PUBLIC)
							  .build());
	}
	
	/**
	 * create setter method
	 * 
	 * @param field
	 * @return
	 */
	default MethodSpec createSetterMethodSpec(VariableElement field, ElementInfo annotationInfo) {
        TypeMirror fieldType = field.asType();
		// create setter method
		String className = annotationInfo.prefix() + annotationInfo.className();
		String packageName = annotationInfo.packageName();
		String setterName = generateSetterName(annotationInfo, field.getSimpleName().toString());
		MethodSpec.Builder setterBuilder = MethodSpec
										   .methodBuilder(setterName)
										   .addModifiers(Modifier.PUBLIC)
										   .addParameter(TypeName.get(fieldType),field.getSimpleName().toString(), new Modifier[0])
										   .addStatement("this.$L = $L", field.getSimpleName().toString(), field.getSimpleName().toString());
		if (annotationInfo.chainedSetters()) {
			setterBuilder.addStatement("return this")
						 .returns(ClassName.get(packageName, className));
		}
		return setterBuilder.build();
	}

	/**
	 * create getter method
	 * @param field
	 * @param fieldClass
	 * @return
	 */
	default MethodSpec createGetterMethodSpec(VariableElement field, TypeName fieldClass, ElementInfo annotationInfo) {
		TypeMirror fieldType = field.asType();
		// create getter method
		String getterName = generateGetterName(annotationInfo, field.getSimpleName().toString(), fieldType.toString().equals(Boolean.class.getName()));
		String fieldName = field.getSimpleName().toString();
		MethodSpec getter = MethodSpec
				.methodBuilder(getterName)
				.addModifiers(Modifier.PUBLIC)
				.addStatement("return "+fieldName)
				.returns(TypeName.get(fieldType))
				.build();
		return getter;
	}

	/**
	 * create field
	 * 
	 * @param field
	 * @param fieldClass
	 * @return
	 */
	default FieldSpec createFieldSpec(VariableElement field, TypeName fieldClass) {
		FieldSpec fieldspec = FieldSpec.builder(fieldClass, field.getSimpleName().toString(), Modifier.PRIVATE).build();
		return fieldspec;
	}

	/**
	 * @param annotationInfo
	 * @param fieldName
	 * @param isBoolean
	 * @return
	 */
	default String generateGetterName(ElementInfo annotationInfo, String fieldName, Boolean isBoolean) {
		if (annotationInfo.fluentAccessors()) {
			return StringUtil.uncapitalise(fieldName);
		} else {			
			return ((isBoolean!=null&& isBoolean.equals(Boolean.TRUE))?"is":"get")+StringUtil.capitalise(fieldName);
		}
	}
	
	/**
	 * @param annotationInfo
	 * @param fieldName
	 * @return
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
	 * @param packageName
	 * @param className
	 * @param annotationInfo
	 * @param methods
	 */
	default void createToString(String packageName, String className, ElementInfo annotationInfo, List<MethodSpec> methods) {
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
	 * @param field
	 * @return
	 */
	default boolean isMethodFinalPrivateStatic(VariableElement field) {
		return (field.getModifiers().contains(Modifier.FINAL) &&
				field.getModifiers().contains(Modifier.PRIVATE) &&
				field.getModifiers().contains(Modifier.STATIC));
	}
}
