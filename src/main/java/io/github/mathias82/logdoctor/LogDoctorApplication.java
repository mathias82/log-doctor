package io.github.mathias82.logdoctor;

import io.github.mathias82.logdoctor.cli.AnalyzeCommand;
import io.github.mathias82.logdoctor.web.LogDoctorWebServer;

import java.util.Arrays;

public class LogDoctorApplication {

    private static final int DEFAULT_WEB_PORT = 8080;

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--web")) {
            LogDoctorWebServer.start(resolveWebPort(args));
            return;
        }

        AnalyzeCommand.run(args);
    }

    static int resolveWebPort(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--port=")) {
                return validPort(arg.substring("--port=".length()));
            }
            if ("--port".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--port requires a value");
                }
                return validPort(args[i + 1]);
            }
        }
        return DEFAULT_WEB_PORT;
    }

    private static int validPort(String raw) {
        try {
            int port = Integer.parseInt(raw);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port: " + raw, e);
        }
    }
}
