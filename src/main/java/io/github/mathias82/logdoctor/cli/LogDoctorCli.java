package io.github.mathias82.logdoctor.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "logdoctor",
        mixinStandardHelpOptions = true,
        version = "logdoctor 0.1.0",
        description = "Analyze Java/JVM logs and explain failures.",
        subcommands = { AnalyzeCommand.class }
)
public class LogDoctorCli implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exit = new CommandLine(new LogDoctorCli()).execute(args);
        System.exit(exit);
    }
}