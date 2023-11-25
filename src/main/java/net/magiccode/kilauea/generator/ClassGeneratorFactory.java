/**
 * kilauea
 * 
 * Published under Apache-2.0 license (https://github.com/CodeWeazle/kilauea/blob/main/LICENSE)
 * 
 * Code: https://github.com/CodeWeazle/kilauea
 * 
 * @author CodeWeazle (2023)
 * 
 * Filename: ClassGeneratorFactory.java
 */

package net.magiccode.kilauea.generator;

import java.util.List;
import java.util.Map;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;

import com.squareup.javapoet.ClassName;

/**
 * 
 */
public class ClassGeneratorFactory {

	
	public static ClassGenerator getClassGenerator(GeneratorType generatorType, 
												   ProcessingEnvironment procEnv, 
												   Filer filer, 
												   Messager messager,
												   final ElementInfo annotationInfo,
												   final ClassName annotatedClass,
												   final Map<ClassName, List<ElementInfo>> input) {
		
		switch (generatorType) {
		
			case JSON:
				return new JSONClassGenerator(procEnv, filer, messager, annotationInfo, annotatedClass, input);
				
			case XML:
				return new XMLClassGenerator(procEnv, filer, messager, annotationInfo, annotatedClass, input);
	
			default:
				return new PlainClassGenerator(procEnv, filer, messager, annotationInfo, annotatedClass, input);
		
		}
	}
	
}
