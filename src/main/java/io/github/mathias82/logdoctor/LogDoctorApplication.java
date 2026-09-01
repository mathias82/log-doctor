package io.github.mathias82.logdoctor;

import io.github.mathias82.logdoctor.cli.AnalyzeCommand;
import io.github.mathias82.logdoctor.web.LogDoctorWebServer;

import java.util.Arrays;

public class LogDoctorApplication {

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--web")) {
            LogDoctorWebServer.start(8080);
            return;
        }

        AnalyzeCommand.run(args);
    }
}
