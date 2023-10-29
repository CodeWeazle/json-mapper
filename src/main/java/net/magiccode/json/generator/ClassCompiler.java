/**
 * 
 */
package net.magiccode.json.generator;

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

/**
 * 
 */
public class ClassCompiler {

	public void compileClass(File javaFile) {
		
	  // JavaCompiler
		// java version supported by tool provider
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		for(SourceVersion sv:compiler.getSourceVersions()){
		   System.out.println(sv);
		}
		
		
	  // JavaCompiler
//      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
      // DiagnosticsCollector
      DiagnosticCollector< JavaFileObject > diagnosticsCollector = new DiagnosticCollector<>();
      
      try( 
    	  StandardJavaFileManager fileManager = compiler.getStandardFileManager( diagnosticsCollector, null, null ) ) {    	  
    	  fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(new File("./target")));
    	  
         File file = javaFile; 
//        		 new File("/Users/volker/workspace_lazy/lazy-developer-01/src/main/java/net/magiccode/lazy/ExampleCode01.java" 
        		 //CompilerExample.class.getResource("ExmpleCode01.java").toURI() 
//        		 );
         
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
