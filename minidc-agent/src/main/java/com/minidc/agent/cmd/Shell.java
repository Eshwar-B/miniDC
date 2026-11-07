package com.minidc.agent.cmd;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Shell command execution wrapper with timeout management and process stream capturing.
 */
@Component
public class Shell {

    public ShellResult run(List<String> cmd, Duration timeout) throws IOException, InterruptedException, TimeoutException {
        Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        if (!completed) {
            process.destroyForcibly();
            throw new TimeoutException("Command timed out after " + timeout + ": " + String.join(" ", cmd));
        }

        return new ShellResult(process.exitValue(), output);
    }
}

