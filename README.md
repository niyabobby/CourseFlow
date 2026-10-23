# COURSEFLOW – College Course Registration & Waitlist Management

## Project Overview
COURSEFLOW is a web-based application designed to simplify college course registration and waitlist management. It allows students to register for courses, monitor seat availability, manage student records, and handle course withdrawals. The project demonstrates the practical application of data structures in an academic environment.

## Objectives
* Manage student registration records efficiently.
* Monitor course capacity and seat availability.
* Manage course waitlists using the First-In-First-Out (FIFO) principle.
* Allow users to register, search, update, and withdraw student records.
* Automatically allocate available seats to waitlisted students.
* Demonstrate the application of data structures in a real-world scenario.

## Features
* Course listing and seat availability monitoring
* Student registration and course selection
* Automatic waitlist management when courses are full
* FIFO-based waitlist processing
* Automatic promotion of waitlisted students when seats become available
* Student record search and name updates
* Course creation with configurable seat capacity
* Display of enrolled students and waitlisted students
* Simple, interactive user interface
* Browser-based data persistence using Local Storage

## Technologies Used
* HTML - Structures the user interface
* CSS - Styling and layout
* JavaScript - Application functionality and registration logic
* Java 
* Browser Local Storage - Local Data Persistance

## Data Structures Used
* Linked List: Demonstrates the management of student enrollment records.
* Queue: Represents the course waitlist and follows the FIFO principle to ensure fair seat allocation.

## Implementation of the Project
The project is implemented as a web application using HTML, CSS, and JavaScript. HTML provides the structure, CSS handles the layout and styling, and JavaScript implements the registration, course management, and waitlist operations.

When a student registers for a course, the system checks the available seats. If seats are available, the student is enrolled; otherwise, the student is added to the waitlist. When an enrolled student withdraws, the first student in the waitlist is automatically promoted if a seat becomes available.

The web application uses browser Local Storage to retain data between sessions. A separate Java console-based implementation demonstrates the core registration and waitlist functionality using Java data structures.

## How to Use the Project
1. Open the live website or download the project repository.
2. View the available courses and their seat capacities.
3. Register students by entering their details and selecting a course.
4. Search for student records or update student names.
5. Withdraw students from courses and observe automatic waitlist promotion.
6. Add new courses and view enrolled and waitlisted students.

## Live Website
https://niyabobby.github.io/CourseFlow/

## GitHub Repository
https://github.com/niyabobby/CourseFlow

## Limitations
* The web application stores data in browser Local Storage rather than a centralized database.
* The current project does not include a backend server or centralized multi-user data management.
* The project is intended for educational purposes.

## Future Enhancements
* Integrate the frontend with a Java backend using Spring Boot.
* Add a database for centralized and permanent data storage.
* Implement student and administrator authentication.
* Introduce course prerequisites and semester management.
* Generate registration reports and course enrollment statistics.

## Conclusion
COURSEFLOW demonstrates how data structure concepts can be applied to college course registration and waitlist management. By combining an interactive web interface with registration logic and FIFO-based waitlist processing, the project provides a practical example of how technology can simplify academic administration.
