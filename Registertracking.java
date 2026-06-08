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
    List<String> parameters=new ArrayList<>();
    String fieldname;
}

class classinfo{
    String name;
    String superclass;
    List<methodinfo> methods=new ArrayList<>();
    List<fieldinfo> fields=new ArrayList<>();
}
class methodinfo {
    String name;
    List<String> parameters=new ArrayList<>();
}
class fieldinfo{
    String name;
}




public class Registertracking{
    List<ReflectionSite> sites=new ArrayList<>();
    Map<String,String> stringregisters=new HashMap<>();
    Map<String,String> classregisters=new HashMap<>();
    Map<String,List<String>> parameterarrays=new HashMap<>();
    List<String> pendingarray=null;
    Map<String,String> classliteralregisters=new HashMap<>();

    String pendingclassforname=null;

    public void clearregisters(){
        stringregisters.clear();
        classregisters.clear();
        pendingclassforname=null;
        classliteralregisters.clear();
        parameterarrays.clear();
        pendingarray=null;
    }

    public void scanreflection(File[] files) throws Exception{
        for(File f:files){
            List<String> lines=Files.readAllLines(f.toPath());
            Map<String,String> registers=new HashMap<>();

            for(int i=0;i<lines.size();i++){
                String line=lines.get(i).trim();
                if(line.startsWith(".method")){
                    clearregisters();
                }
                trackconststring(line);
                trackmoveobject(line);
                trackmoveresultobject(line);

                trackconstclass(line);
                trackprimitivetype(line);
                trackfillednewarray(line);
                trackarraymoveresult(line);
                if(line.contains("Ljava/lang/Class;->forName(")){
                    processForName(line,f.getName());
                }

                else if(line.contains("Ljava/lang/Class;->getMethod(")){
                    processMemberReflection(line,f.getName(),"getMethod",true);
                }
                else if(line.contains("Ljava/lang/Class;->getDeclaredMethods(")){
                    processMemberReflection(line,f.getName(),"getDeclaredMethods",true);
                }
                else if(line.contains("Ljava/lang/Class;->getField(")){
                    processMemberReflection(line,f.getName(),"getField",false);
                }
                else if(line.contains("Ljava/lang/Class;->getDeclaredField(")){
                    processMemberReflection(line,f.getName(),"getDeclaredField",false);
                }
            }
        }
    }

    public void trackconstclass(String line){           //Function to trace the arguments of a variable
        if(!line.startsWith("const-class")){
             return;
        }
        int comma=line.indexOf(',');
        if(comma==-1){
            return;
        }

        String left=line.substring(0,comma);
        String reg=left.substring(left.lastIndexOf(' ')+1).trim();
        String clazz=line.substring(comma+1).trim();
        if(clazz.startsWith("L") && clazz.endsWith(";")){
            clazz=clazz.substring(1,clazz.length()-1);
            clazz=clazz.replace('/','.');
        }
        classliteralregisters.put(reg,clazz);
    }

    public void trackprimitivetype(String line){
        if(!line.startsWith("sget-object"))
            return;

        if(!line.contains("->TYPE:"))
            return;

        int comma=line.indexOf(',');
        if(comma==-1)
            return;

        String left=line.substring(0,comma);
        String reg=left.substring(left.lastIndexOf(' ')+1).trim();

        String primitive=null;
        if(line.contains("Ljava/lang/Integer;->TYPE")){
            primitive="int";
        }
        if(line.contains("Ljava/lang/Boolean;->TYPE")){
            primitive="boolean";
        }
        if(line.contains("Ljava/lang/Long;->TYPE")){
            primitive="long";
        }
        if(line.contains("Ljava/lang/Float;->TYPE")){
            primitive="float";
        }
        if(line.contains("Ljava/lang/Double;->TYPE")){
            primitive="double";
        }
        if(line.contains("Ljava/lang/Short;->TYPE")){
            primitive="short";
        }
        if(line.contains("Ljava/lang/Byte;->TYPE")){
            primitive="byte";
        }
        if(line.contains("Ljava/lang/Character;->TYPE")){
            primitive="char";
        }
        if(primitive!=null){
            classliteralregisters.put(reg,primitive);
        }
    }

    public void trackfillednewarray(String line){
        if(!line.startsWith("filled-new-array")){
            return;
        }
        if(!line.contains("[Ljava/lang/Class;"))
            return;

        int start=line.indexOf('{');
        int end=line.indexOf('}');

        if(start==-1 || end==-1 || start>=end)
            return;

        String inside=line.substring(start+1,end);

        String[] regs=inside.split(",");
        List<String> params=new ArrayList<>();

        for(String r:regs){
            r=r.trim();
            String type=classliteralregisters.get(r);
            if(type!=null){
                params.add(type);
            }
        }
        pendingarray=params;
    }

    public void trackarraymoveresult(String line){
        if(!line.startsWith("move-result-object")){
            return;
        }
        if(pendingarray==null){
            return;
        }
        String reg=line.substring(line.lastIndexOf(' ')+1).trim();
        parameterarrays.put(reg,new ArrayList<>(pendingarray));
        pendingarray=null;
    }



    public void trackconststring(String line){
        if(!line.startsWith("const-string")){
            return;
        }
        int comma=line.indexOf(',');
        if(comma==-1){
            return;
        }
        String left=line.substring(0,comma);
        String reg=left.substring(left.lastIndexOf(' ')+1).trim();

        int first=line.indexOf('"');
        int last=line.lastIndexOf('"');

        if(first==-1 || last==-1 || last<=first){
            return;
        }
        String value=line.substring(first+1,last);

        stringregisters.put(reg,value);

    }

    public void trackmoveobject(String line){
        if(!line.startsWith("move-object"))
            return;

        String args=line.substring(line.indexOf(' ')+1);
        String[] parts=args.split(",");
        if(parts.length!=2){
            return;
        }
        String dest=parts[0].trim();
        String src=parts[1].trim();

        if(stringregisters.containsKey(src)){
            stringregisters.put(dest,stringregisters.get(src));
        }

        if(classregisters.containsKey(src)){
            classregisters.put(dest,classregisters.get(src));
        }

        if(parameterarrays.containsKey(src)){
            parameterarrays.put(dest,new ArrayList<>(parameterarrays.get(src)));
        }
    }

    public void trackmoveresultobject(String line){
        if(!line.startsWith("move-result-object")){
            return;
        }
        if(pendingclassforname==null){
            return;
        }
        String reg=line.substring(line.lastIndexOf(' ')+1).trim();

        classregisters.put(reg,pendingclassforname);
        pendingclassforname=null;
    }




    public void processForName(String line,String file){
        int start=line.indexOf('{');
        int end=line.indexOf('}');

        if(start==-1 || end==-1 || end<=start){
            return;
        }
        String reg=line.substring(start+1,end).trim();
        String classname=stringregisters.get(reg);

        if(classname==null)
            return;
        ReflectionSite site=new ReflectionSite();
        site.smalifile=file;
        site.reflectiontype="Class.forName";
        site.classname=classname;
        sites.add(site);
        pendingclassforname=classname;

    }


    public void processMemberReflection(String line,String file,String reflectiontype,boolean ismethod){
        int start= line.indexOf('{');
        int end=line.indexOf('}');

        if(start==-1 || end==-1 || start>=end)
            return;

        String args=line.substring(start+1,end);
        String[] regs=args.split(",");

        if(regs.length<2)
            return;

        String classreg=regs[0].trim();
        String memberreg=regs[1].trim();

        String classname=classregisters.get(classreg);
        String membername=stringregisters.get(memberreg);

        if(classname==null || membername==null)
            return;

        ReflectionSite site=new ReflectionSite();
        site.smalifile=file;
        site.reflectiontype=reflectiontype;
        site.classname=classname;

        if(ismethod && regs.length>=3){
            String paramreg=regs[2].trim();

            List<String> params=parameterarrays.get(paramreg);
            if(params!=null){
                site.parameters.addAll(params);
            }

            site.methodname=membername;
        }
        else{
            site.fieldname=membername;
        }
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