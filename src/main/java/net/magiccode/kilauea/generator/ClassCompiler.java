/**
 * kilauea
 * 
 * ClassCompiler compiles given java file into class file.
 *
 * Published under Apache-2.0 license (https://github.com/CodeWeazle/kilauea/blob/main/LICENSE)
 * 
 * Code: https://github.com/CodeWeazle/kilauea
 * 
 * @author CodeWeazle (2023)
 * 
 * Filename: ClassCompiler.java
 */
package net.magiccode.kilauea.generator;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import lombok.NoArgsConstructor;

@NoArgsConstructor

/**
 * Uses system java compiler to compile given .java file 
 */
public class ClassCompiler {

	/**
	 * Compiles the given file and creates a .class file.
	 * 
	 * @param javaFile describes the file containing the Java code to be compiles.
	 */
	public void compileClass(File javaFile) {
		
		// JavaCompiler
		// java version supported by tool provider
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		for(SourceVersion sv:compiler.getSourceVersions()){
		   System.out.println(sv);
		}
		
		
      // DiagnosticsCollector
      DiagnosticCollector< JavaFileObject > diagnosticsCollector = new DiagnosticCollector<>();
      
      try( 
    	  StandardJavaFileManager fileManager = compiler.getStandardFileManager( diagnosticsCollector, null, null ) ) {    	  
    	  fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(new File("./target/classes")));
    	  
         File file = javaFile; 
         Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjectsFromFiles( Arrays.asList( file ) );
         
         JavaCompiler.CompilationTask task = compiler.getTask( null, 
        		 											   fileManager, 
        		 											   diagnosticsCollector, 
        		 											   null,
        		 											   null, 
        		 											   sources );
         task.call();
      } catch (IOException e) {
		e.printStackTrace();
	}
      for( Diagnostic < ? extends JavaFileObject > d: diagnosticsCollector.getDiagnostics() ) {
         System.out.format("Line: %d, %s in %s", 
        		 d.getLineNumber(), 
        		 d.getMessage( null ),
        		 d.getSource().getName() );
      }
   }
}
