package io.github.jsvro.example;

import io.github.jsvro.core.JsvroCodec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EnrolledCourseControllerTest {
    private static final String SCHEMA = "{\"jsvro\":\"1\",\"columns\":["
            + "{\"name\":\"course\",\"type\":\"object\",\"columns\":["
            + "{\"name\":\"code\",\"type\":\"string\"},{\"name\":\"title\",\"type\":\"string\"},"
            + "{\"name\":\"credits\",\"type\":\"integer\"},"
            + "{\"name\":\"subject\",\"type\":\"object\",\"columns\":["
            + "{\"name\":\"code\",\"type\":\"string\"},{\"name\":\"name\",\"type\":\"string\"}]},"
            + "{\"name\":\"lecturer\",\"type\":\"object\",\"columns\":["
            + "{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"email\",\"type\":\"string\"}]},"
            + "{\"name\":\"university\",\"type\":\"object\",\"columns\":["
            + "{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"country\",\"type\":\"string\"}]}]},"
            + "{\"name\":\"students\",\"type\":\"array\",\"items\":{\"type\":\"object\",\"columns\":["
            + "{\"name\":\"studentNumber\",\"type\":\"string\"},{\"name\":\"name\",\"type\":\"string\"},"
            + "{\"name\":\"born\",\"type\":\"date\"},"
            + "{\"name\":\"address\",\"type\":\"object\",\"columns\":["
            + "{\"name\":\"street\",\"type\":\"string\"},{\"name\":\"postalCode\",\"type\":\"string\"},"
            + "{\"name\":\"city\",\"type\":\"string\"},{\"name\":\"country\",\"type\":\"string\"}]},"
            + "{\"name\":\"grades\",\"type\":\"array\",\"items\":{\"type\":\"object\",\"columns\":["
            + "{\"name\":\"courseCode\",\"type\":\"string\"},{\"name\":\"letter\",\"type\":\"string\"},"
            + "{\"name\":\"awarded\",\"type\":\"date\"}]}}]}}]}";

    private static final String FIRST_ROW = "[[\"CS2010\",\"Algorithms and Data Structures\",10,"
            + "[\"CS\",\"Computer Science\"],[\"Ingrid Lund\",\"ingrid.lund@example.edu\"],"
            + "[\"Nordic University of Technology\",\"NO\"]],"
            + "[[\"S1001\",\"Alice Hansen\",\"2004-03-12\",[\"Storgata 1\",\"0155\",\"Oslo\",\"NO\"],"
            + "[[\"CS1010\",\"A\",\"2025-06-10\"],[\"MATH1000\",\"B\",\"2025-06-14\"]]],"
            + "[\"S1002\",\"Bob Olsen\",\"2003-11-02\",[\"Bryggen 4\",\"5003\",\"Bergen\",\"NO\"],"
            + "[[\"CS1010\",\"C\",\"2025-06-10\"]]]]]";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsvroCodec codec;

    @Test
    void servesEnrolledCoursesAsJsvro() throws Exception {
        byte[] body = mvc.perform(get("/enrolled-courses").accept("application/vnd.jsvro"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.jsvro"))
                .andReturn().getResponse().getContentAsByteArray();

        List<String> lines = new String(body).lines().toList();
        assertEquals(4, lines.size());
        assertEquals(SCHEMA, lines.get(0));
        assertEquals(FIRST_ROW, lines.get(1));
        assertEquals(new EnrolledCourseController().enrolledCourses(),
                codec.readList(new ByteArrayInputStream(body), EnrolledCourse.class));
    }

    @Test
    void keepsJsonAsTheDefault() throws Exception {
        mvc.perform(get("/enrolled-courses"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
