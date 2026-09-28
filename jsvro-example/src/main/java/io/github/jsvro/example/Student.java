package io.github.jsvro.example;

import java.time.LocalDate;
import java.util.List;

record Student(String studentNumber, String name, LocalDate born, Address address, List<Grade> grades) {}
