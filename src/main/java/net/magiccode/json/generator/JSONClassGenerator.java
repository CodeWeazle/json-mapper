package net.magiccode.json.generator;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import net.magiccode.json.annotation.JSONRequired;
import net.magiccode.json.annotation.JSONTransient;
import net.magiccode.json.util.ReflectionUtil;
import net.magiccode.json.util.StringUtil;

/**
 * Generate a copy 
 */
public class JSONClassGenerator implements ClassGenerator {

	Map<ClassName, List<ElementInfo>> input;
	Filer filer;
	Messager messager;
	ProcessingEnvironment procEnv;

	/**
	 * @param filer
	 * @param input
	 * @param elements
	 */
	public JSONClassGenerator(ProcessingEnvironment procEnv,
							  Filer filer,
							  Messager messager,
							  Map<ClassName, List<ElementInfo>> input) {
		this.filer = filer;
		this.input = input;
		this.messager = messager;
	}

	/**
	 * @throws IOException
	 */
	public void generate() throws IOException {

		for (ClassName key : input.keySet()) {

			input.get(key).stream().forEach(annotationInfo -> {

				try {
					String className = annotationInfo.prefix() + annotationInfo.className();

					messager.printMessage(Diagnostic.Kind.NOTE,"Generating " + className);

					String packageName = generatePackageName(key, annotationInfo);
					List<FieldSpec> fields = new ArrayList<>();
					List<MethodSpec> methods = new ArrayList<>();

					// when using lombok, we only need to generate the fields
					if (annotationInfo.useLombok()) {
						createFields(annotationInfo, fields);						
					} else { // otherwise, we also need getters and setters
						createNoArgsConstructor(packageName, className, methods);
						createFieldsGettersAndSetters(annotationInfo, fields, methods);
						createToString(packageName, className, annotationInfo, methods);
					}
					createOfWithArguments(packageName, className, annotationInfo, methods);
					createOfWithClass(key, packageName, className, annotationInfo, methods);
					createToJSONString(packageName, className, annotationInfo, methods);	
					
					TypeSpec generatedJSONClass = generateClass(annotationInfo, className, packageName, fields, methods);

					JavaFile javaFile = JavaFile.builder(packageName, generatedJSONClass)
												.indent("    ")
												.build();
					javaFile.writeTo(filer);
				} catch (IOException e) {
					messager.printMessage(Diagnostic.Kind.ERROR,"Error occured while generating class "+key+". "+e.getLocalizedMessage());
				}
			});
		}
	}

	/**
	 * @param annotationInfo
	 * @param fields
	 * @param methods
	 */
	private void createFieldsGettersAndSetters(ElementInfo annotationInfo, List<FieldSpec> fields,
			List<MethodSpec> methods) {
		// Generate fields, getters and setters
		for (VariableElement field : annotationInfo.fields()) {
			if (!isMethodFinalPrivateStatic(field)) {

				TypeMirror fieldType = field.asType();
				TypeName fieldClass = TypeName.get(fieldType);
				messager.printMessage(Diagnostic.Kind.NOTE,"Generating field " + field.getSimpleName().toString());
	
				fields.add(createFieldSpec(field, fieldClass));
				methods.add(createGetterMethodSpec(field, fieldClass, annotationInfo));
				methods.add(createSetterMethodSpec(field, annotationInfo));
			}
		}
	}

	
	/**
	 * @param annotationInfo
	 * @param fields
	 * @param methods
	 */
	private void createFields(ElementInfo annotationInfo, List<FieldSpec> fields) {
		// Generate fields
		for (VariableElement field : annotationInfo.fields()) {
			if (!isMethodFinalPrivateStatic(field)) {
				TypeMirror fieldType = field.asType();
				TypeName fieldClass = TypeName.get(fieldType);
				messager.printMessage(Diagnostic.Kind.NOTE,"Generating field " + field.getSimpleName().toString());
				fields.add(createFieldSpec(field, fieldClass));
			}
		}		
	}

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
								   List<MethodSpec> methods) {
		DateTimeFormatter pattern = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		TypeSpec.Builder generatedJSONClassBuilder = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC)
						.addJavadoc(CodeBlock.builder()
											.add(packageName+"."+className+" generated by JsonMapper. (@JsonMapper)\n\n")
											.add("@created "+LocalDateTime.now().format(pattern)+"\n")
										    .build())
						.addSuperinterface(Serializable.class)
						.addField(
								FieldSpec.builder(TypeName.LONG, "serialVersionUID", Modifier.FINAL, Modifier.STATIC)
										 .initializer("-1L")
										 .build())
						.addFields(fields)
						.addMethods(methods)
						.addAnnotation(
								AnnotationSpec.builder(JsonInclude.class)
											.addMember("value", "$T.$L", Include.class, annotationInfo.jsonInclude().name())
											.build());
		// In case we use lombok, we can just generate some annotations
		if (annotationInfo.useLombok()) {
			generatedJSONClassBuilder.addAnnotation(NoArgsConstructor.class);
			if (fields.size()>0)
				generatedJSONClassBuilder.addAnnotation(AllArgsConstructor.class);
			generatedJSONClassBuilder.addAnnotation(Getter.class)
									 .addAnnotation(Setter.class)	
									 .addAnnotation(ToString.class)
									 .addAnnotation(AnnotationSpec.builder(Accessors.class)
												.addMember("fluent", annotationInfo.fluentAccessors()?"true":"false" )
												.build());
		}
		
		// add superclass
		if (annotationInfo.superclass() != null) {
			generatedJSONClassBuilder.superclass(annotationInfo.superclass());
		}
		
		// add provided interface
		if (annotationInfo.interfaces() != null) {
			annotationInfo.interfaces().stream()
				// filter existing classes 
				 .forEach( intf -> {
					 generatedJSONClassBuilder.addSuperinterface(intf);
				 });			 
		}
		TypeSpec generatedJSONClass = generatedJSONClassBuilder.build();

		messager.printMessage(Diagnostic.Kind.NOTE,"Generated " + className);

		return generatedJSONClass;
	}
	
	/**
	 * generate toString method
	 * @param packageName
	 * @param className
	 * @param annotationInfo
	 * @param methods
	 */
	private void createToString(String packageName, String className, ElementInfo annotationInfo, List<MethodSpec> methods) {
		// create toSTring method
		MethodSpec.Builder toStringBuilder = MethodSpec.methodBuilder("toString")
				.addModifiers(Modifier.PUBLIC)
				.addStatement("String stringRep = this.getClass().getName()");
				for (VariableElement field : annotationInfo.fields()) {
					if (!isMethodFinalPrivateStatic(field)) {
						String fieldName = field.getSimpleName().toString();
						String statement = "stringRep += \"$L=\"+$L";
						if(field != annotationInfo.fields().get(annotationInfo.fields().size() - 1))
							statement += "+\", \""; 
						toStringBuilder.addStatement(statement, fieldName, fieldName);
					}
				}
				toStringBuilder.addStatement("stringRep += \")\"")
								.addStatement("return stringRep")
								.addJavadoc(CodeBlock
									    .builder()
									    .add("All field as a comma-separated list.\n")
									    .build());
				
				toStringBuilder.returns(ClassName.get(String.class));
				methods.add(toStringBuilder.build());
	}
	
	/**
	 * generate toString method
	 * @param packageName
	 * @param className
	 * @param annotationInfo
	 * @param methods
	 */
	private void createToJSONString(String packageName, String className, ElementInfo annotationInfo, List<MethodSpec> methods) {
		// create toJSONString method
		MethodSpec.Builder toStringBuilder = MethodSpec.methodBuilder("toJSONString")
				.addModifiers(Modifier.PUBLIC)
				
				.addStatement("$T mapper = new $T()", ObjectMapper.class, ObjectMapper.class)
				.addStatement("String value = this.getClass().getName()")
				.beginControlFlow("try")
					.addStatement("value += mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)")
				.endControlFlow()
				.beginControlFlow("catch ($T e)",JsonProcessingException.class)
						.addStatement("e.printStackTrace()")
				.endControlFlow()
				.addStatement("return value")
				.returns(ClassName.get(String.class));
				methods.add(toStringBuilder.build());
	}
	
	
	/**
	 * @param packageName
	 * @param className
	 * @param annotationInfo
	 * @param methods
	 */
	private void createOfWithArguments(String packageName, String className, ElementInfo annotationInfo, List<MethodSpec> methods) {
		// create of method
		MethodSpec.Builder of = MethodSpec.methodBuilder("of")
				.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
				.addStatement(className+" newJsonObect = new "+className+"();")
				.addJavadoc(CodeBlock
					    .builder()
					    .add("Creates object with all given values, acts basically as a AllArgsConstructor.\n")
					    .build());
				for (VariableElement field : annotationInfo.fields()) {
					if (!isMethodFinalPrivateStatic(field)) {
						TypeMirror fieldType = field.asType();
						TypeName fieldClass = TypeName.get(fieldType);
						of.addParameter(fieldClass ,field.getSimpleName().toString(), new Modifier[0]);
					}
				};
				for (VariableElement field : annotationInfo.fields()) {
					if (!isMethodFinalPrivateStatic(field)) {						
						String setterName = generateSetterName(annotationInfo, field.getSimpleName().toString());
						of.addStatement("newJsonObect.$L($L)", setterName, field.getSimpleName().toString());
					}
			    }
				of.addStatement("return newJsonObect")
				
				.returns(ClassName.get(packageName, className));
				methods.add(of.build());
	}
	
	/**
	 * Create constructor taking the source class and creating the json mapped class.
	 * 
	 * @param key
	 * @param packageName
	 * @param className
	 * @param annotationInfo
	 * @param methods
	 */
	private void createOfWithClass(ClassName key, String packageName, String className, ElementInfo annotationInfo, List<MethodSpec> methods) {
		// create of method
		String incomingObjectName = "incoming"+key.simpleName();
		
		MethodSpec.Builder of = MethodSpec.methodBuilder("of")
				.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
				//.addParameter(key, incomingObjectName, new Modifier[0])
				.addParameter(ClassName.get(Object.class), incomingObjectName, new Modifier[0])
				.addStatement(className +" newJsonObect = new "+className+"()")
		
				.addJavadoc(CodeBlock
					    .builder()
					    .add("Creates object from source class. Since source class has not been compiled at this point,\n")
					    .add("calling the setters on the source would lead to an exception.\n")
					    .add("For this reason the getter call is wrapped by reflection.\n\n")
					    .add("@param $L - the incoming object of type $L to be mapped.", incomingObjectName, key.simpleName())
					    .build());
		
				AtomicInteger fieldCount = new AtomicInteger(0);
				annotationInfo.fields().stream().filter(field -> ! isMethodFinalPrivateStatic(field))
												.forEach(field -> {
																	
//				for (VariableElement field : annotationInfo.fields()) {
//					if (!(field.getModifiers().contains(Modifier.FINAL) &&
//						 field.getModifiers().contains(Modifier.PRIVATE) &&
//						 field.getModifiers().contains(Modifier.STATIC))) {

						TypeMirror fieldType = field.asType();
						TypeName fieldClass = TypeName.get(fieldType);
						String fieldName = field.getSimpleName().toString();
						String setterName = generateSetterName(annotationInfo, field.getSimpleName().toString());
						String localFieldName = "field"+fieldCount.getAndIncrement();
						of
							.beginControlFlow("try")
								  .addStatement("$T $L = $T.deepGetField($L, $S, true)", Field.class,
										  												 localFieldName,
										  												 ReflectionUtil.class, 
										  												 className+".class", 
										  												 fieldName)
						
								  .beginControlFlow("if ($L != null)" , localFieldName)
								  
//								  .beginControlFlow("if ($L.getClass().getDeclaredField($L) != null)", incomingObjectName, StringUtil.quote(fieldName))								   		
//									.addStatement("newJsonObect.$L(($L)$T.invokeGetterMethod($L, $L.getClass().getDeclaredField($L)))",  
									.addStatement("newJsonObect.$L(($L)$T.invokeGetterMethod($L, $L))",  
													  setterName,
											   		  fieldClass,
											   		  ReflectionUtil.class, 
											   		  incomingObjectName,
											   		  localFieldName)
								  .endControlFlow()
							  .endControlFlow()
								  .beginControlFlow("catch ($T e)", IllegalAccessException.class)
										.addStatement("// ignore for now")
								  .endControlFlow();
				});
				of.addStatement("return newJsonObect")
				.returns(ClassName.get(packageName, className));
				methods.add(of.build());
	}

	/**
	 * create field
	 * 
	 * @param field
	 * @param fieldClass
	 * @return
	 */
	@Override
	public FieldSpec createFieldSpec(VariableElement field, TypeName fieldClass) {

		FieldSpec fieldspec = null;
		if (field.getAnnotation(JSONTransient.class) == null) {			
			AnnotationSpec.Builder jsonPropertyAnnotationBuilder = AnnotationSpec.builder(JsonProperty.class)
					.addMember("value", StringUtil.quote(StringUtil.camelToSnake(field.getSimpleName().toString()), '"'));
			if(field.getAnnotation(JSONRequired.class) != null) {
				jsonPropertyAnnotationBuilder.addMember("required", "true");
			}
			AnnotationSpec jsonPropertyAnnotation = jsonPropertyAnnotationBuilder.build();
			
			fieldspec = FieldSpec.builder(fieldClass, field.getSimpleName().toString(), Modifier.PRIVATE)
					.addAnnotation(jsonPropertyAnnotation).build();
			
		} else {
			fieldspec = FieldSpec.builder(fieldClass, field.getSimpleName().toString(), Modifier.PRIVATE)
					.addAnnotation(JsonIgnore.class)
					.build();
			
		}
 		
		return fieldspec;
	}

	/**
	 * @param key
	 * @param annotationInfo
	 * @return
	 */
	private String generatePackageName(ClassName key, ElementInfo annotationInfo) {
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
	 * @param field
	 * @return
	 */
	private static boolean isMethodFinalPrivateStatic(VariableElement field) {
		return (field.getModifiers().contains(Modifier.FINAL) &&
				field.getModifiers().contains(Modifier.PRIVATE) &&
				field.getModifiers().contains(Modifier.STATIC));
	}

	
}

