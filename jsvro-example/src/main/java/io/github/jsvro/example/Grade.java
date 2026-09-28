package io.github.jsvro.example;

import java.time.LocalDate;

record Grade(String courseCode, GradeLetter letter, LocalDate awarded) {}
