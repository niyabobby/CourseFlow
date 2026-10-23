\
import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class CourseFlow {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
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
        Course(String id, String name, int capacity) {
            this.id=id; this.name=name; this.capacity=capacity;
        }
    }

    public static void main(String[] args) throws Exception {
        loadData();
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);
        server.createContext("/api/", CourseFlow::handleApi);
        server.createContext("/", CourseFlow::serveStatic);
        server.setExecutor(null);
        server.start();
        System.out.println("CourseFlow running at http://localhost:8080");
        System.out.println("Press Ctrl+C to stop.");
    }

    private static synchronized void handleApi(HttpExchange ex) throws IOException {
        addCors(ex);
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        if ("OPTIONS".equalsIgnoreCase(method)) { ex.sendResponseHeaders(204, -1); return; }
        try {
            JsonObject body = readBody(ex);
            switch (path) {
                case "/api/courses":
                    if ("GET".equals(method)) sendJson(ex, 200, dashboard());
                    else if ("POST".equals(method)) addCourse(ex, body);
                    else sendError(ex, 405, "Method not allowed");
                    break;
                case "/api/register":
                    requireMethod(ex, method, "POST");
                    register(ex, body);
                    break;
                case "/api/withdraw":
                    requireMethod(ex, method, "POST");
                    withdraw(ex, body);
                    break;
                case "/api/student":
                    requireMethod(ex, method, "GET");
                    searchStudent(ex, query(ex, "id"));
                    break;
                case "/api/student/update":
                    requireMethod(ex, method, "POST");
                    updateStudent(ex, body);
                    break;
                case "/api/reset":
                    requireMethod(ex, method, "POST");
                    resetData();
                    saveData();
                    sendJson(ex, 200, dashboard());
                    break;
                default: sendError(ex, 404, "API endpoint not found");
            }
        } catch (StopRequest e) {
            // The method error response has already been sent.
        } catch (IllegalArgumentException e) {
            sendError(ex, 400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "Server error: " + e.getMessage());
        }
    }

    private static void addCourse(HttpExchange ex, JsonObject b) throws IOException {
        String id = required(b, "id"), name = required(b, "name");
        int capacity = integer(b, "capacity");
        if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive.");
        if (courses.containsKey(id)) throw new IllegalArgumentException("Course ID already exists.");
        Course c = new Course(id, name, capacity);
        courses.put(id, c); saveData();
        sendJson(ex, 201, dashboard());
    }

    private static void register(HttpExchange ex, JsonObject b) throws IOException {
        String courseId = required(b, "courseId"), id = required(b, "id"), name = required(b, "name");
        Course c = courses.get(courseId);
        if (c == null) throw new IllegalArgumentException("Course not found.");
        if (c.enrolled.contains(id) || c.waitlist.contains(id))
            throw new IllegalArgumentException("Student is already registered for this course.");
        Student s = students.get(id);
        if (s == null) students.put(id, new Student(id, name)); else s.name = name;
        String message;
        String status;
        if (c.enrolled.size() < c.capacity) {
            c.enrolled.add(id); status = "Enrolled";
            message = name + " registered successfully for " + c.name + ".";
        } else {
            c.waitlist.add(id); status = "Waitlisted";
            message = c.name + " is full. " + name + " was added to the waitlist.";
        }
        saveData();
        JsonObject out = dashboard(); out.addProperty("message", message); out.addProperty("status", status);
        sendJson(ex, 200, out);
    }

    private static void withdraw(HttpExchange ex, JsonObject b) throws IOException {
        String courseId = required(b, "courseId"), id = required(b, "id");
        Course c = courses.get(courseId);
        if (c == null) throw new IllegalArgumentException("Course not found.");
        String message;
        if (c.waitlist.remove(id)) {
            message = studentName(id) + " was removed from the waitlist.";
        } else if (c.enrolled.remove(id)) {
            message = studentName(id) + " was withdrawn from " + c.name + ".";
            if (!c.waitlist.isEmpty()) {
                String promoted = c.waitlist.remove(0);
                c.enrolled.add(promoted);
                message += " " + studentName(promoted) + " was promoted from the waitlist.";
            }
        } else {
            throw new IllegalArgumentException("Student not found in this course.");
        }
        saveData();
        JsonObject out = dashboard(); out.addProperty("message", message);
        sendJson(ex, 200, out);
    }

    private static void searchStudent(HttpExchange ex, String id) throws IOException {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Student ID is required.");
        id = id.trim();
        Student s = students.get(id);
        JsonObject out = new JsonObject();
        if (s == null) {
            out.add("student", JsonNull.INSTANCE);
            out.add("registrations", new JsonArray());
        } else {
            out.add("student", GSON.toJsonTree(s));
            JsonArray registrations = new JsonArray();
            for (Course c : courses.values()) {
                String status = c.enrolled.contains(id) ? "Enrolled" :
                    c.waitlist.contains(id) ? "Waitlisted" : null;
                if (status != null) {
                    JsonObject r = new JsonObject();
                    r.addProperty("courseId", c.id); r.addProperty("courseName", c.name);
                    r.addProperty("status", status); registrations.add(r);
                }
            }
            out.add("registrations", registrations);
        }
        sendJson(ex, 200, out);
    }

    private static void updateStudent(HttpExchange ex, JsonObject b) throws IOException {
        String id = required(b, "id"), name = required(b, "name");
        Student s = students.get(id);
        if (s == null) throw new IllegalArgumentException("Student not found.");
        s.name = name; saveData();
        JsonObject out = dashboard(); out.addProperty("message", "Student record updated successfully.");
        sendJson(ex, 200, out);
    }

    private static JsonObject dashboard() {
        JsonObject out = new JsonObject();
        JsonArray list = new JsonArray();
        int enrolledTotal=0, seatsTotal=0, waitingTotal=0;
        for (Course c : courses.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", c.id); o.addProperty("name", c.name); o.addProperty("capacity", c.capacity);
            o.add("enrolled", GSON.toJsonTree(c.enrolled));
            o.add("waitlist", GSON.toJsonTree(c.waitlist));
            JsonArray enrolledDetails = new JsonArray();
            for (String id : c.enrolled) enrolledDetails.add(studentJson(id));
            JsonArray waitingDetails = new JsonArray();
            for (String id : c.waitlist) waitingDetails.add(studentJson(id));
            o.add("enrolledStudents", enrolledDetails); o.add("waitingStudents", waitingDetails);
            list.add(o);
            enrolledTotal += c.enrolled.size();
            seatsTotal += Math.max(0, c.capacity - c.enrolled.size());
            waitingTotal += c.waitlist.size();
        }
        out.add("courses", list); out.addProperty("totalCourses", courses.size());
        out.addProperty("totalEnrolled", enrolledTotal); out.addProperty("totalSeats", seatsTotal);
        out.addProperty("totalWaiting", waitingTotal);
        return out;
    }

    private static JsonObject studentJson(String id) {
        Student s = students.get(id);
        JsonObject o = new JsonObject();
        o.addProperty("id", id); o.addProperty("name", s == null ? "Unknown student" : s.name);
        return o;
    }
    private static String studentName(String id) {
        Student s=students.get(id); return s == null ? id : s.name;
    }
    private static String required(JsonObject b, String key) {
        if (b == null || !b.has(key) || b.get(key).isJsonNull()) throw new IllegalArgumentException(key + " is required.");
        String v=b.get(key).getAsString().trim();
        if (v.isEmpty()) throw new IllegalArgumentException(key + " cannot be empty.");
        return v;
    }
    private static int integer(JsonObject b, String key) {
        try { return Integer.parseInt(required(b,key)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(key + " must be a number."); }
    }
    private static JsonObject readBody(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) return new JsonObject();
        try (InputStream in=ex.getRequestBody()) {
            String s=new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return s.isBlank() ? new JsonObject() : JsonParser.parseString(s).getAsJsonObject();
        }
    }
    private static String query(HttpExchange ex, String key) {
        String q=ex.getRequestURI().getRawQuery();
        if (q == null) return null;
        for (String pair:q.split("&")) {
            String[] kv=pair.split("=",2);
            if (URLDecoder.decode(kv[0], StandardCharsets.UTF_8).equals(key))
                return kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
        }
        return null;
    }
    private static void requireMethod(HttpExchange ex, String actual, String expected) throws IOException {
        if (!expected.equalsIgnoreCase(actual)) { sendError(ex,405,"Method not allowed"); throw new StopRequest(); }
    }
    private static class StopRequest extends RuntimeException {}
    private static void sendError(HttpExchange ex, int code, String message) throws IOException {
        JsonObject o=new JsonObject(); o.addProperty("error",message); sendJson(ex,code,o);
    }
    private static void sendJson(HttpExchange ex, int code, JsonElement data) throws IOException {
        byte[] bytes=GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control","no-store");
        ex.sendResponseHeaders(code,bytes.length);
        try(OutputStream os=ex.getResponseBody()){os.write(bytes);}
    }
    private static void addCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin","*");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers","Content-Type");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods","GET,POST,OPTIONS");
    }
    private static void serveStatic(HttpExchange ex) throws IOException {
        String path=ex.getRequestURI().getPath();
        if (path.equals("/")) path="/index.html";
        Path file=WEB_ROOT.resolve(path.substring(1)).normalize();
        if (!file.startsWith(WEB_ROOT) || !Files.isRegularFile(file)) {
            byte[] b="Not found".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404,b.length); try(OutputStream os=ex.getResponseBody()){os.write(b);} return;
        }
        String type=file.toString().endsWith(".html")?"text/html; charset=utf-8":
                    file.toString().endsWith(".css")?"text/css; charset=utf-8":
                    file.toString().endsWith(".js")?"application/javascript; charset=utf-8":"application/octet-stream";
        byte[] b=Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type",type);
        ex.sendResponseHeaders(200,b.length); try(OutputStream os=ex.getResponseBody()){os.write(b);}
    }

    private static void resetData() {
        courses.clear(); students.clear();
        courses.put("101",new Course("101","Java Programming",2));
        courses.put("102",new Course("102","Database Management",3));
        courses.put("103",new Course("103","Data Structures",2));
    }
    private static void loadData() {
        try {
            if (Files.exists(DATA_FILE)) {
                JsonObject root=JsonParser.parseString(Files.readString(DATA_FILE)).getAsJsonObject();
                if (root.has("courses")) for (JsonElement e:root.getAsJsonArray("courses")) {
                    JsonObject o=e.getAsJsonObject();
                    Course c=new Course(o.get("id").getAsString(),o.get("name").getAsString(),o.get("capacity").getAsInt());
                    if(o.has("enrolled")) for(JsonElement x:o.getAsJsonArray("enrolled")) c.enrolled.add(x.getAsString());
                    if(o.has("waitlist")) for(JsonElement x:o.getAsJsonArray("waitlist")) c.waitlist.add(x.getAsString());
                    courses.put(c.id,c);
                }
                if(root.has("students")) for(Map.Entry<String,JsonElement> e:root.getAsJsonObject("students").entrySet()) {
                    JsonObject o=e.getValue().getAsJsonObject();
                    students.put(e.getKey(),new Student(e.getKey(),o.get("name").getAsString()));
                }
                if(!courses.isEmpty()) return;
            }
        } catch(Exception e) { System.err.println("Could not load saved data; restoring sample data."); }
        resetData(); saveData();
    }
    private static void saveData() {
        try {
            JsonObject root=new JsonObject(); root.add("courses",GSON.toJsonTree(courses.values()));
            root.add("students",GSON.toJsonTree(students)); Files.writeString(DATA_FILE,GSON.toJson(root));
        } catch(IOException e) { throw new RuntimeException("Could not save data",e); }
    }
}
