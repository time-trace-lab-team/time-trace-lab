package org.example;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppTest {

    @Test
    void mainPrintsExpectedMessage() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();

        try (PrintStream testOut = new PrintStream(capturedOutput, true, StandardCharsets.UTF_8)) {
            System.setOut(testOut);
            App.main(new String[0]);
        } finally {
            System.setOut(originalOut);
        }

        assertEquals(
                "Hello World!" + System.lineSeparator(),
                capturedOutput.toString(StandardCharsets.UTF_8)
        );
    }
}
