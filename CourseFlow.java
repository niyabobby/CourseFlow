
import java.util.*;

// Student class
class Student {
    int id;
    String name;

    Student(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public String toString() {
        return "ID: " + id + " | Name: " + name;
    }
}

// Course class
class Course {
    int courseId;
    String courseName;
    int capacity;

    LinkedList<Student> enrolled = new LinkedList<>();
    Queue<Student> waitlist = new LinkedList<>();

    Course(int courseId, String courseName, int capacity) {
        this.courseId = courseId;
        this.courseName = courseName;
        this.capacity = capacity;
    }

    boolean containsStudent(int id) {
        for (Student s : enrolled) {
            if (s.id == id) return true;
        }

        for (Student s : waitlist) {
            if (s.id == id) return true;
        }

        return false;
    }

    void registerStudent(Student s) {
        if (containsStudent(s.id)) {
            System.out.println("Student already registered for this course.");
            return;
        }

        if (enrolled.size() < capacity) {
            enrolled.add(s);
            System.out.println("Registration successful!");
        } else {
            waitlist.offer(s);
            System.out.println("Course is full. Added to waitlist.");
        }
    }

    void withdrawStudent(int id) {
        Student found = null;

        for (Student s : enrolled) {
            if (s.id == id) {
                found = s;
                break;
            }
        }

        if (found != null) {
            enrolled.remove(found);
            System.out.println("Student withdrawn successfully.");

            // Promote the first student in the waitlist
            if (!waitlist.isEmpty()) {
                Student next = waitlist.poll();
                enrolled.add(next);
                System.out.println("Waitlisted student promoted: " + next.name);
            }
            return;
        }

        // Also allow a student to cancel their waitlist entry
        Iterator<Student> it = waitlist.iterator();

        while (it.hasNext()) {
            Student s = it.next();

            if (s.id == id) {
                it.remove();
                System.out.println("Student removed from waitlist.");
                return;
            }
        }

        System.out.println("Student not found in this course.");
    }

    void displayStudents() {
        System.out.println("\n--- Enrolled Students ---");

        if (enrolled.isEmpty()) {
            System.out.println("No enrolled students.");
        } else {
            for (Student s : enrolled) {
                System.out.println(s);
            }
        }

        System.out.println("\n--- Waiting Queue (FIFO) ---");

        if (waitlist.isEmpty()) {
            System.out.println("Waitlist is empty.");
        } else {
            for (Student s : waitlist) {
                System.out.println(s);
            }
        }

        System.out.println("\nSeats available: "
                + (capacity - enrolled.size()));

        System.out.println("Total waitlisted: " + waitlist.size());
    }

    Student findStudent(int id) {
        for (Student s : enrolled) {
            if (s.id == id) return s;
        }

        for (Student s : waitlist) {
            if (s.id == id) return s;
        }

        return null;
    }
}

// Main application
public class CourseFlow {
    static Scanner sc = new Scanner(System.in);
    static ArrayList<Course> courses = new ArrayList<>();

    static int readInt(String message) {
        while (true) {
            System.out.print(message);
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid number.");
            }
        }
    }

    static Course findCourse(int id) {
        for (Course c : courses) {
            if (c.courseId == id) return c;
        }
        return null;
    }

    static void displayCourses() {
        System.out.println("\n--- Available Courses ---");

        for (Course c : courses) {
            System.out.println(
                "Course ID: " + c.courseId
                + " | Name: " + c.courseName
                + " | Capacity: " + c.capacity
                + " | Enrolled: " + c.enrolled.size()
                + " | Available seats: "
                + (c.capacity - c.enrolled.size())
            );
        }
    }

    static void addCourse() {
        int id = readInt("Enter course ID: ");

        if (findCourse(id) != null) {
            System.out.println("Course ID already exists.");
            return;
        }

        System.out.print("Enter course name: ");
        String name = sc.nextLine().trim();

        if (name.isEmpty()) {
            System.out.println("Course name cannot be empty.");
            return;
        }

        int capacity = readInt("Enter seat capacity: ");

        if (capacity <= 0) {
            System.out.println("Capacity must be positive.");
            return;
        }

        courses.add(new Course(id, name, capacity));
        System.out.println("Course added successfully.");
    }

    static void register() {
        displayCourses();

        int courseId = readInt("Enter course ID: ");
        Course c = findCourse(courseId);

        if (c == null) {
            System.out.println("Course not found.");
            return;
        }

        int id = readInt("Enter student ID: ");

        if (c.containsStudent(id)) {
            System.out.println("Student already registered for this course.");
            return;
        }

        System.out.print("Enter student name: ");
        String name = sc.nextLine().trim();

        if (name.isEmpty()) {
            System.out.println("Student name cannot be empty.");
            return;
        }

        c.registerStudent(new Student(id, name));
    }

    static void withdraw() {
        int courseId = readInt("Enter course ID: ");
        Course c = findCourse(courseId);

        if (c == null) {
            System.out.println("Course not found.");
            return;
        }

        int id = readInt("Enter student ID: ");
        c.withdrawStudent(id);
    }

    static void searchStudent() {
        int courseId = readInt("Enter course ID: ");
        Course c = findCourse(courseId);

        if (c == null) {
            System.out.println("Course not found.");
            return;
        }

        int id = readInt("Enter student ID: ");
        Student s = c.findStudent(id);

        if (s == null) {
            System.out.println("Student not found.");
        } else {
            System.out.println("Student found: " + s);

            if (c.enrolled.contains(s)) {
                System.out.println("Status: Enrolled");
            } else {
                System.out.println("Status: Waitlisted");
            }
        }
    }

    static void updateStudent() {
        int courseId = readInt("Enter course ID: ");
        Course c = findCourse(courseId);

        if (c == null) {
            System.out.println("Course not found.");
            return;
        }

        int id = readInt("Enter student ID: ");
        Student s = c.findStudent(id);

        if (s == null) {
            System.out.println("Student not found.");
            return;
        }

        System.out.print("Enter new student name: ");
        String newName = sc.nextLine().trim();

        if (newName.isEmpty()) {
            System.out.println("Name cannot be empty.");
            return;
        }

        s.name = newName;
        System.out.println("Student record updated successfully.");
    }

    public static void main(String[] args) {
        // Sample courses
        courses.add(new Course(101, "Java Programming", 2));
        courses.add(new Course(102, "Database Management", 3));
        courses.add(new Course(103, "Data Structures", 2));

        int choice;

        do {
            System.out.println("\n===== COURSEFLOW =====");
            System.out.println("1. View Courses");
            System.out.println("2. Add Course");
            System.out.println("3. Register Student");
            System.out.println("4. Withdraw Student");
            System.out.println("5. Search Student");
            System.out.println("6. Update Student");
            System.out.println("7. View Enrolled Students and Waitlist");
            System.out.println("8. Exit");

            choice = readInt("Enter your choice: ");

            switch (choice) {
                case 1:
                    displayCourses();
                    break;

                case 2:
                    addCourse();
                    break;

                case 3:
                    register();
                    break;

                case 4:
                    withdraw();
                    break;

                case 5:
                    searchStudent();
                    break;

                case 6:
                    updateStudent();
                    break;

                case 7: {
                    int id = readInt("Enter course ID: ");
                    Course c = findCourse(id);

                    if (c != null) {
                        c.displayStudents();
                    } else {
                        System.out.println("Course not found.");
                    }
                    break;
                }

                case 8:
                    System.out.println("Thank you for using COURSEFLOW!");
                    break;

                default:
                    System.out.println("Invalid choice. Try again.");
            }

        } while (choice != 8);

        sc.close();
    }
}
