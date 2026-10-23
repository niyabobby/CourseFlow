import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** CourseFlow web backend. Requires only Java 17+; no Maven or external libraries. */
public class CourseFlow {
    private static final Path DATA_FILE = Paths.get("courseflow-data.json");
    private static final Path WEB_ROOT = Paths.get(".").toAbsolutePath().normalize();
    private static final Map<String, Course> courses = new LinkedHashMap<>();
    private static final Map<String, Student> students = new LinkedHashMap<>();

    static class Student {
        String id, name;
        Student(String id, String name) { this.id = id; this.name = name; }
    }
    static class Course {
        String id, name;
        int capacity;
        List<String> enrolled = new ArrayList<>();
        List<String> waitlist = new ArrayList<>();
        Course(String id, String name, int capacity) { this.id=id; this.name=name; this.capacity=capacity; }
    }

    public static void main(String[] args) throws Exception {
        loadData();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
        server.createContext("/api/", CourseFlow::handleApi);
        server.createContext("/", CourseFlow::serveStatic);
        server.setExecutor(null);
        server.start();
        System.out.println("CourseFlow is running at http://localhost:8080");
        System.out.println("Keep this window open. Press Ctrl+C to stop the server.");
    }

    private static synchronized void handleApi(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        try {
            if ("OPTIONS".equalsIgnoreCase(method)) { ex.sendResponseHeaders(204, -1); return; }
            Map<String,Object> body = "POST".equalsIgnoreCase(method) ? readBody(ex) : new LinkedHashMap<>();
            switch (path) {
                case "/api/courses":
                    if ("GET".equalsIgnoreCase(method)) sendJson(ex, 200, dashboard());
                    else if ("POST".equalsIgnoreCase(method)) addCourse(ex, body);
                    else sendError(ex, 405, "Method not allowed");
                    break;
                case "/api/register": requireMethod(ex, method, "POST"); register(ex, body); break;
                case "/api/withdraw": requireMethod(ex, method, "POST"); withdraw(ex, body); break;
                case "/api/student": requireMethod(ex, method, "GET"); searchStudent(ex, query(ex, "id")); break;
                case "/api/student/update": requireMethod(ex, method, "POST"); updateStudent(ex, body); break;
                case "/api/reset":
                    requireMethod(ex, method, "POST"); resetData(); saveData(); sendJson(ex, 200, dashboard()); break;
                default: sendError(ex, 404, "API endpoint not found");
            }
        } catch (StopRequest ignored) {
        } catch (IllegalArgumentException e) {
            sendError(ex, 400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            if (ex.getResponseCode() < 0) sendError(ex, 500, "Server error: " + e.getMessage());
        }
    }

    private static void addCourse(HttpExchange ex, Map<String,Object> b) throws IOException {
        String id=required(b,"id"), name=required(b,"name"); int capacity=integer(b,"capacity");
        if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive.");
        if (courses.containsKey(id)) throw new IllegalArgumentException("Course ID already exists.");
        courses.put(id,new Course(id,name,capacity)); saveData(); sendJson(ex,201,dashboard());
    }
    private static void register(HttpExchange ex, Map<String,Object> b) throws IOException {
        String courseId=required(b,"courseId"), id=required(b,"id"), name=required(b,"name");
        Course c=courses.get(courseId); if(c==null) throw new IllegalArgumentException("Course not found.");
        if(c.enrolled.contains(id)||c.waitlist.contains(id)) throw new IllegalArgumentException("Student is already registered for this course.");
        Student s=students.get(id); if(s==null) students.put(id,new Student(id,name)); else s.name=name;
        String message, status;
        if(c.enrolled.size()<c.capacity){c.enrolled.add(id);status="Enrolled";message=name+" registered successfully for "+c.name+".";}
        else{c.waitlist.add(id);status="Waitlisted";message=c.name+" is full. "+name+" was added to the waitlist.";}
        saveData(); Map<String,Object> out=dashboard();out.put("message",message);out.put("status",status);sendJson(ex,200,out);
    }
    private static void withdraw(HttpExchange ex, Map<String,Object> b) throws IOException {
        String courseId=required(b,"courseId"),id=required(b,"id");Course c=courses.get(courseId);
        if(c==null)throw new IllegalArgumentException("Course not found.");String message;
        if(c.waitlist.remove(id)) message=studentName(id)+" was removed from the waitlist.";
        else if(c.enrolled.remove(id)){message=studentName(id)+" was withdrawn from "+c.name+".";if(!c.waitlist.isEmpty()){String promoted=c.waitlist.remove(0);c.enrolled.add(promoted);message+=" "+studentName(promoted)+" was promoted from the waitlist.";}}
        else throw new IllegalArgumentException("Student not found in this course.");
        saveData();Map<String,Object> out=dashboard();out.put("message",message);sendJson(ex,200,out);
    }
    private static void searchStudent(HttpExchange ex,String id)throws IOException{
        if(id==null||id.trim().isEmpty())throw new IllegalArgumentException("Student ID is required.");id=id.trim();Student s=students.get(id);
        Map<String,Object> out=new LinkedHashMap<>();out.put("student",s==null?null:studentMap(s));List<Object> regs=new ArrayList<>();
        if(s!=null)for(Course c:courses.values()){String status=c.enrolled.contains(id)?"Enrolled":c.waitlist.contains(id)?"Waitlisted":null;if(status!=null){Map<String,Object> r=new LinkedHashMap<>();r.put("courseId",c.id);r.put("courseName",c.name);r.put("status",status);regs.add(r);}}
        out.put("registrations",regs);sendJson(ex,200,out);
    }
    private static void updateStudent(HttpExchange ex,Map<String,Object>b)throws IOException{
        String id=required(b,"id"),name=required(b,"name");Student s=students.get(id);if(s==null)throw new IllegalArgumentException("Student not found.");s.name=name;saveData();Map<String,Object>out=dashboard();out.put("message","Student record updated successfully.");sendJson(ex,200,out);
    }
    private static Map<String,Object> dashboard(){
        List<Object> list=new ArrayList<>();int enrolledTotal=0,seatsTotal=0,waitingTotal=0;
        for(Course c:courses.values()){
            Map<String,Object> o=new LinkedHashMap<>();o.put("id",c.id);o.put("name",c.name);o.put("capacity",c.capacity);o.put("enrolled",new ArrayList<>(c.enrolled));o.put("waitlist",new ArrayList<>(c.waitlist));
            List<Object> enrolledDetails=new ArrayList<>(),waitingDetails=new ArrayList<>();for(String id:c.enrolled)enrolledDetails.add(studentMapById(id));for(String id:c.waitlist)waitingDetails.add(studentMapById(id));
            o.put("enrolledStudents",enrolledDetails);o.put("waitingStudents",waitingDetails);list.add(o);enrolledTotal+=c.enrolled.size();seatsTotal+=Math.max(0,c.capacity-c.enrolled.size());waitingTotal+=c.waitlist.size();
        }
        Map<String,Object> out=new LinkedHashMap<>();out.put("courses",list);out.put("totalCourses",courses.size());out.put("totalEnrolled",enrolledTotal);out.put("totalSeats",seatsTotal);out.put("totalWaiting",waitingTotal);return out;
    }
    private static Map<String,Object> studentMapById(String id){Student s=students.get(id);Map<String,Object>m=new LinkedHashMap<>();m.put("id",id);m.put("name",s==null?"Unknown student":s.name);return m;}
    private static Map<String,Object> studentMap(Student s){Map<String,Object>m=new LinkedHashMap<>();m.put("id",s.id);m.put("name",s.name);return m;}
    private static String studentName(String id){Student s=students.get(id);return s==null?id:s.name;}
    private static String required(Map<String,Object>b,String key){Object v=b.get(key);if(v==null||String.valueOf(v).trim().isEmpty())throw new IllegalArgumentException(key+" is required.");return String.valueOf(v).trim();}
    private static int integer(Map<String,Object>b,String key){try{return Integer.parseInt(required(b,key));}catch(NumberFormatException e){throw new IllegalArgumentException(key+" must be a number.");}}
    @SuppressWarnings("unchecked") private static Map<String,Object> readBody(HttpExchange ex)throws IOException{try(InputStream in=ex.getRequestBody()){String s=new String(in.readAllBytes(),StandardCharsets.UTF_8);if(s.isBlank())return new LinkedHashMap<>();Object parsed=Json.parse(s);if(!(parsed instanceof Map))throw new IllegalArgumentException("Invalid JSON request.");return (Map<String,Object>)parsed;}}
    private static String query(HttpExchange ex,String key){String q=ex.getRequestURI().getRawQuery();if(q==null)return null;for(String pair:q.split("&")){String[]kv=pair.split("=",2);if(URLDecoder.decode(kv[0],StandardCharsets.UTF_8).equals(key))return kv.length>1?URLDecoder.decode(kv[1],StandardCharsets.UTF_8):"";}return null;}
    private static void requireMethod(HttpExchange ex,String actual,String expected)throws IOException{if(!expected.equalsIgnoreCase(actual)){sendError(ex,405,"Method not allowed");throw new StopRequest();}}
    private static class StopRequest extends RuntimeException{}
    private static void sendError(HttpExchange ex,int code,String message)throws IOException{Map<String,Object>o=new LinkedHashMap<>();o.put("error",message);sendJson(ex,code,o);}
    private static void sendJson(HttpExchange ex,int code,Object data)throws IOException{byte[]bytes=Json.stringify(data).getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");ex.getResponseHeaders().set("Cache-Control","no-store");ex.sendResponseHeaders(code,bytes.length);try(OutputStream os=ex.getResponseBody()){os.write(bytes);}}
    private static void serveStatic(HttpExchange ex)throws IOException{
        String path=ex.getRequestURI().getPath();if(path.equals("/"))path="/index.html";Path file=WEB_ROOT.resolve(path.substring(1)).normalize();
        if(!file.startsWith(WEB_ROOT)||!Files.isRegularFile(file)){byte[]b="Not found".getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(404,b.length);try(OutputStream os=ex.getResponseBody()){os.write(b);}return;}
        String f=file.toString();String type=f.endsWith(".html")?"text/html; charset=utf-8":f.endsWith(".css")?"text/css; charset=utf-8":f.endsWith(".js")?"application/javascript; charset=utf-8":"application/octet-stream";byte[]b=Files.readAllBytes(file);ex.getResponseHeaders().set("Content-Type",type);ex.sendResponseHeaders(200,b.length);try(OutputStream os=ex.getResponseBody()){os.write(b);}
    }
    private static void resetData(){courses.clear();students.clear();courses.put("101",new Course("101","Java Programming",2));courses.put("102",new Course("102","Database Management",3));courses.put("103",new Course("103","Data Structures",2));}
    @SuppressWarnings("unchecked") private static void loadData(){
        try{if(Files.exists(DATA_FILE)){Object rootObj=Json.parse(Files.readString(DATA_FILE));if(rootObj instanceof Map){Map<String,Object>root=(Map<String,Object>)rootObj;Object cs=root.get("courses");if(cs instanceof List)for(Object item:(List<?>)cs){Map<String,Object>o=(Map<String,Object>)item;Course c=new Course(String.valueOf(o.get("id")),String.valueOf(o.get("name")),((Number)o.get("capacity")).intValue());if(o.get("enrolled") instanceof List)for(Object x:(List<?>)o.get("enrolled"))c.enrolled.add(String.valueOf(x));if(o.get("waitlist") instanceof List)for(Object x:(List<?>)o.get("waitlist"))c.waitlist.add(String.valueOf(x));courses.put(c.id,c);}Object ss=root.get("students");if(ss instanceof Map)for(Map.Entry<String,Object>e:((Map<String,Object>)ss).entrySet()){Map<String,Object>o=(Map<String,Object>)e.getValue();students.put(e.getKey(),new Student(e.getKey(),String.valueOf(o.get("name"))));}if(!courses.isEmpty())return;}}}catch(Exception e){System.err.println("Could not load saved data; restoring sample data.");}resetData();saveData();
    }
    private static void saveData(){List<Object>cs=new ArrayList<>();for(Course c:courses.values()){Map<String,Object>o=new LinkedHashMap<>();o.put("id",c.id);o.put("name",c.name);o.put("capacity",c.capacity);o.put("enrolled",c.enrolled);o.put("waitlist",c.waitlist);cs.add(o);}Map<String,Object>ss=new LinkedHashMap<>();for(Student s:students.values())ss.put(s.id,studentMap(s));Map<String,Object>root=new LinkedHashMap<>();root.put("courses",cs);root.put("students",ss);try{Files.writeString(DATA_FILE,Json.stringify(root),StandardCharsets.UTF_8);}catch(IOException e){throw new RuntimeException("Could not save data",e);}}

    /** Small JSON parser/writer so the application has no third-party dependencies. */
    static class Json {
        static Object parse(String s){Parser p=new Parser(s);Object v=p.value();p.ws();if(p.i!=s.length())throw new IllegalArgumentException("Invalid JSON.");return v;}
        static String stringify(Object v){StringBuilder b=new StringBuilder();write(v,b);return b.toString();}
        static void write(Object v,StringBuilder b){if(v==null){b.append("null");}else if(v instanceof String){b.append('"');for(char c:((String)v).toCharArray()){switch(c){case '"':b.append("\\\"");break;case '\\':b.append("\\\\");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;default:if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}b.append('"');}else if(v instanceof Number||v instanceof Boolean){b.append(v);}else if(v instanceof Map){b.append('{');boolean first=true;for(Object entryObj:((Map<?,?>)v).entrySet()){Map.Entry<?,?>e=(Map.Entry<?,?>)entryObj;if(!first)b.append(',');first=false;write(String.valueOf(e.getKey()),b);b.append(':');write(e.getValue(),b);}b.append('}');}else if(v instanceof Iterable){b.append('[');boolean first=true;for(Object x:(Iterable<?>)v){if(!first)b.append(',');first=false;write(x,b);}b.append(']');}else{write(String.valueOf(v),b);}}
        static class Parser {final String s;int i=0;Parser(String s){this.s=s;}void ws(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}Object value(){ws();if(i>=s.length())throw new IllegalArgumentException("Invalid JSON.");char c=s.charAt(i);if(c=='{')return object();if(c=='[')return array();if(c=='"')return string();if(c=='t'&&s.startsWith("true",i)){i+=4;return true;}if(c=='f'&&s.startsWith("false",i)){i+=5;return false;}if(c=='n'&&s.startsWith("null",i)){i+=4;return null;}return number();}Map<String,Object> object(){Map<String,Object>m=new LinkedHashMap<>();i++;ws();if(i<s.length()&&s.charAt(i)=='}'){i++;return m;}while(true){ws();if(i>=s.length()||s.charAt(i)!='"')throw new IllegalArgumentException("Invalid JSON object.");String k=string();ws();if(i>=s.length()||s.charAt(i++)!=':')throw new IllegalArgumentException("Invalid JSON object.");m.put(k,value());ws();if(i<s.length()&&s.charAt(i)=='}'){i++;return m;}if(i>=s.length()||s.charAt(i++)!=',')throw new IllegalArgumentException("Invalid JSON object.");}}List<Object> array(){List<Object>a=new ArrayList<>();i++;ws();if(i<s.length()&&s.charAt(i)==']'){i++;return a;}while(true){a.add(value());ws();if(i<s.length()&&s.charAt(i)==']'){i++;return a;}if(i>=s.length()||s.charAt(i++)!=',')throw new IllegalArgumentException("Invalid JSON array.");}}String string(){i++;StringBuilder b=new StringBuilder();while(i<s.length()){char c=s.charAt(i++);if(c=='"')return b.toString();if(c=='\\'){if(i>=s.length())break;char e=s.charAt(i++);switch(e){case '"':b.append('"');break;case '\\':b.append('\\');break;case '/':b.append('/');break;case 'b':b.append('\b');break;case 'f':b.append('\f');break;case 'n':b.append('\n');break;case 'r':b.append('\r');break;case 't':b.append('\t');break;case 'u':if(i+4>s.length())throw new IllegalArgumentException("Invalid JSON escape.");b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;break;default:throw new IllegalArgumentException("Invalid JSON escape.");}}else b.append(c);}throw new IllegalArgumentException("Unterminated JSON string.");}Number number(){int start=i;if(i<s.length()&&s.charAt(i)=='-')i++;while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;if(i<s.length()&&s.charAt(i)=='.'){i++;while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;}if(start==i)throw new IllegalArgumentException("Invalid JSON value.");String n=s.substring(start,i);try{return n.contains(".")?Double.parseDouble(n):Long.parseLong(n);}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid JSON number.");}}}
    }
}
