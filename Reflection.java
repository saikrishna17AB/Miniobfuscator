package org.example;
import java.io.*;
import java.nio.file.Files;
import java.sql.Ref;
import java.util.*;
import java.nio.file.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

class ReflectionSite{
    String ownerclass;
    String ownermethod;
    String smalifile;

    String reflectiontype;

    String classname;
    String methodname;
    String fieldname;
}

public class Reflection{
    List<ReflectionSite> sites=new ArrayList<>();

    public void scanreflection(File[] files) throws Exception{
        for(File f:files){
            List<String> lines=Files.readAllLines(f.toPath());
            for(int i=0;i<lines.size();i++){
                String line=lines.get(i);
                if(line.contains("Ljava/lang/Class;->forName(")){
                    processForName(lines,i,f.getName());
                }
                else if(line.contains("Ljava/lang/Class->getMethod(")){
                    processGetMethod(lines,i,f.getName());
                }
                else if(line.contains("Ljava/lang/Class->getField(")){
                    processGetField(lines,i,f.getName());
                }
            }
        }
    }

    public String findprevstring(List<String> lines,int index){
        for(int i=index-1;i>=0;i--){
            String line=lines.get(i).trim();
            if(line.startsWith("const-string")){
                int first=line.indexOf('"');
                int last=line.lastIndexOf('"');
                if(first!=-1 && last !=1 && last>first){
                    return line.substring(first+1,last);
                }
            }
        }
        return null;
    }
    public void processForName(List<String> lines,int index,String file){
        String classname=findprevstring(lines,index);
        if(classname==null){
            return;
        }

        ReflectionSite site=new ReflectionSite();
        site.smalifile=file;
        site.reflectiontype="Class.forName";
        site.classname=classname;
        sites.add(site);
    }

    public void processGetMethod(List<String> lines,int index,String file){
        String methodname=findprevstring(lines,index);
        if(methodname==null){
            return;
        }
        ReflectionSite site=new ReflectionSite();
        site.smalifile=file;
        site.reflectiontype="getMethod";
        site.methodname=methodname;
        sites.add(site);
    }

    public void processGetField(List<String> lines,int index,String file){
        String fieldname=findprevstring(lines,index);
        if(fieldname==null){
            return;
        }

        ReflectionSite site=new ReflectionSite();
        site.smalifile=file;
        site.reflectiontype="getField";
        site.fieldname=fieldname;
        sites.add(site);
    }

    public void writekeeprules(String output) throws Exception{

        Map<String,Set<String>> methods=new HashMap<>();
        Map<String,Set<String>> fields=new HashMap<>();
        Set<String> classes=new HashSet<>();

        for(ReflectionSite site:sites){
            if(site.classname!=null){
                classes.add(site.classname);
            }
        }

        StringBuilder sb=new StringBuilder();

        for(String cls:classes){
            sb.append("-keep class ");
            sb.append(cls);
            sb.append(" {\n");

            Set<String> m=methods.get(cls);

            if(m!=null){
                for(String name:m){
                    sb.append("    * ");
                    sb.append(name);
                    sb.append("(...);\n");

                }
            }
            Set<String> f=fields.get(cls);

            if(f!=null){
                for(String name:f){
                    sb.append("    <fields>;\n");
                }
            }
            sb.append("}\n\n");
        }

        Files.writeString(Paths.get(output),sb.toString());


    }



}