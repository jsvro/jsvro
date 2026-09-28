package io.github.jsvro.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/enrolled-courses")
class EnrolledCourseController {
    private static final University UNIVERSITY = new University("Nordic University of Technology", "NO");

    private static final Subject COMPUTER_SCIENCE = new Subject("CS", "Computer Science");
    private static final Subject MATHEMATICS = new Subject("MATH", "Mathematics");

    private static final Lecturer INGRID = new Lecturer("Ingrid Lund", "ingrid.lund@example.edu");
    private static final Lecturer MAGNUS = new Lecturer("Magnus Berg", "magnus.berg@example.edu");

    private static final Course ALGORITHMS = new Course("CS2010", "Algorithms and Data Structures", 10, COMPUTER_SCIENCE, INGRID, UNIVERSITY);
    private static final Course DATABASES = new Course("CS2300", "Database Systems", 10, COMPUTER_SCIENCE, MAGNUS, UNIVERSITY);
    private static final Course LINEAR_ALGEBRA = new Course("MATH1100", "Linear Algebra", 5, MATHEMATICS, MAGNUS, UNIVERSITY);

    private static final Student ALICE = new Student("S1001", "Alice Hansen", LocalDate.of(2004, 3, 12),
            new Address("Storgata 1", "0155", "Oslo", "NO"),
            List.of(new Grade("CS1010", GradeLetter.A, LocalDate.of(2025, 6, 10)),
                    new Grade("MATH1000", GradeLetter.B, LocalDate.of(2025, 6, 14))));

    private static final Student BOB = new Student("S1002", "Bob Olsen", LocalDate.of(2003, 11, 2),
            new Address("Bryggen 4", "5003", "Bergen", "NO"),
            List.of(new Grade("CS1010", GradeLetter.C, LocalDate.of(2025, 6, 10))));

    private static final Student CAROL = new Student("S1003", "Carol Berg", LocalDate.of(2005, 1, 20),
            new Address("Kongens gate 7", "7011", "Trondheim", "NO"),
            List.of());

    @GetMapping
    List<EnrolledCourse> enrolledCourses() {
        return List.of(
                new EnrolledCourse(ALGORITHMS, List.of(ALICE, BOB)),
                new EnrolledCourse(DATABASES, List.of(ALICE, CAROL)),
                new EnrolledCourse(LINEAR_ALGEBRA, List.of(BOB, CAROL)));
    }
}
