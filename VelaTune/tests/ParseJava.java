import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;
public class ParseJava {
 public static void main(String[] args)throws Exception {
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
  try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,null,null)){
   List<java.io.File> files=new ArrayList<>();try(var stream=Files.walk(Path.of(args[0]))){stream.filter(p->p.toString().endsWith(".java")).forEach(p->files.add(p.toFile()));}
   JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none"),null,manager.getJavaFileObjectsFromFiles(files));task.parse();
   boolean ok=true;for(var d:diagnostics.getDiagnostics()){System.out.println(d);if(d.getKind()==Diagnostic.Kind.ERROR)ok=false;}
   if(!ok)throw new AssertionError("Java syntax errors");System.out.println("PASS Java syntax parse: "+files.size()+" files. Android type checking NOT performed.");
  }
 }
}
