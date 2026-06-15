package org.tkit.onecx.onecxsvcgen;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import org.tkit.onecx.onecxsvcgen.commands.AddEntityCommand;
import org.tkit.onecx.onecxsvcgen.commands.BatchModelCommand;
import org.tkit.onecx.onecxsvcgen.commands.CreateSvcCommand;
import picocli.CommandLine.Command;

@TopCommand
@Command(
        name = "onecx-svc-generator",
        mixinStandardHelpOptions = true,
        description = "Stable JAR-based generator for OneCX SVC projects",
        subcommands = {
                CreateSvcCommand.class,
                AddEntityCommand.class,
                BatchModelCommand.class
        }
)
public class Main {
}