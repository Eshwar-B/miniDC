package com.minidc.agent.cmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests validating process execution, output capturing, and timeout terminations in Shell helper.
 */
class ShellTest {

    private final Shell shell = new Shell();

    @Test
    @DisplayName("Shell executes echo command successfully and captures output")
    void testEchoCommand() throws Exception {
        boolean isWin = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> cmd = isWin
                ? List.of("cmd.exe", "/c", "echo", "minidc-test-echo")
                : List.of("echo", "minidc-test-echo");

        ShellResult result = shell.run(cmd, Duration.ofSeconds(5));
        assertEquals(0, result.code());
        assertTrue(result.out().contains("minidc-test-echo"));
    }

    @Test
    @DisplayName("Shell times out and forcibly terminates long running processes")
    void testCommandTimeout() {
        boolean isWin = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> cmd = isWin
                ? List.of("powershell", "-NoProfile", "-Command", "Start-Sleep -Seconds 4")
                : List.of("sleep", "4");

        assertThrows(TimeoutException.class, () ->
                shell.run(cmd, Duration.ofMillis(300))
        );
    }
}

