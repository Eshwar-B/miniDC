package com.minidc.agent.cmd;

/**
 * Result record containing process exit status and standard/error output.
 */
public record ShellResult(
        int code,
        String out
) {
    public boolean isSuccess() {
        return code == 0;
    }
}

