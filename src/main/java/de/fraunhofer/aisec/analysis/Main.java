
package de.fraunhofer.aisec.analysis;

import de.fraunhofer.aisec.analysis.server.AnalysisServer;
import de.fraunhofer.aisec.analysis.structures.AnalysisContext;
import de.fraunhofer.aisec.analysis.structures.Finding;
import de.fraunhofer.aisec.analysis.structures.ServerConfiguration;
import de.fraunhofer.aisec.analysis.structures.TypestateMode;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/** Start point of the standalone analysis server. */
@SuppressWarnings("java:S106")
@Command(name = "codyze", mixinStandardHelpOptions = true, version = "1.5.0", description = "Codyze finds security flaws in source code", sortOptions = false, usageHelpWidth = 100)
public class Main implements Callable<Integer> {
	private static final Logger log = LoggerFactory.getLogger(Main.class);

	@CommandLine.ArgGroup(exclusive = true, multiplicity = "1", heading = "Execution mode\n")
	private ExecutionMode executionMode;

	@CommandLine.ArgGroup(exclusive = false, heading = "Analysis settings\n")
	private final AnalysisMode analysisMode = new AnalysisMode();

	@CommandLine.ArgGroup(exclusive = false, heading = "Translation settings\n")
	private final TranslationSettings translationSettings = new TranslationSettings();

	@Option(names = { "-s", "--source" }, paramLabel = "<path>", description = "Source file or folder to analyze.")
	private File analysisInput;

	@Option(names = { "-m",
			"--mark" }, paramLabel = "<path>", description = "Load MARK policy files from folder", defaultValue = "./", showDefaultValue = CommandLine.Help.Visibility.ON_DEMAND)
	private File markFolderName;

	@Option(names = { "-o",
			"--output" }, paramLabel = "<file>", description = "Write results to file. Use - for stdout.", defaultValue = "findings.json", showDefaultValue = CommandLine.Help.Visibility.ON_DEMAND)
	private String outputFile;

	@Option(names = {
			"--timeout" }, paramLabel = "<minutes>", description = "Terminate analysis after timeout", defaultValue = "120", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
	private long timeout;

	@Option(names = {
			"--no-good-findings" }, description = "Disable output of \"positive\" findings which indicate correct implementations", showDefaultValue = CommandLine.Help.Visibility.ON_DEMAND)
	private boolean disableGoodFindings;

	public static void main(String... args) {
		int exitCode = new CommandLine(new Main()).execute(args);
		System.exit(exitCode);
	}

	@Override
	public Integer call() throws Exception {
		Instant start = Instant.now();

		if (analysisMode.tsMode == null) {
			analysisMode.tsMode = TypestateMode.NFA;
		}

		AnalysisServer server = AnalysisServer.builder()
				.config(ServerConfiguration.builder()
						.launchLsp(executionMode.lsp)
						.launchConsole(executionMode.tui)
						.typestateAnalysis(analysisMode.tsMode)
						.disableGoodFindings(disableGoodFindings)
						.analyzeIncludes(translationSettings.analyzeIncludes)
						.includePath(translationSettings.includesPath)
						.markFiles(markFolderName.getAbsolutePath())
						.build())
				.build();

		server.start();
		log.info("Analysis server started in {} in ms.", Duration.between(start, Instant.now()).toMillis());

		if (!executionMode.lsp && analysisInput != null) {
			log.info("Analyzing {}", analysisInput);
			AnalysisContext ctx = server.analyze(analysisInput.getAbsolutePath())
					.get(timeout, TimeUnit.MINUTES);

			var findings = ctx.getFindings();

			writeFindings(findings);

			if (executionMode.cli) {
				// Return code based on the existence of violations
				return findings.stream().anyMatch(Finding::isProblem) ? 1 : 0;
			}
		} else if (executionMode.lsp) {
			// Block main thread. Work is done in
			Thread.currentThread().join();
		}

		return 0;
	}

	private void writeFindings(Set<Finding> findings) {
		StringBuilder sb = new StringBuilder("[");
		Iterator<Finding> it = findings.iterator();
		while (it.hasNext()) {
			Finding f = it.next();
			JSONObject jFinding = new JSONObject(f);
			sb.append(jFinding.toString(2));
			if (it.hasNext()) {
				sb.append(",");
			}
		}
		sb.append("]");

		if (outputFile.equals("-")) {
			System.out.println(sb.toString());
		} else {
			try (PrintWriter out = new PrintWriter(new File(outputFile))) {
				out.println(sb.toString());
			}
			catch (FileNotFoundException e) {
				System.out.println(e.getMessage());
			}
		}

	}
}

/**
 * Codyze runs in any of three modes:
 *
 * CLI: Non-interactive command line client. Accepts arguments from command line and runs analysis.
 *
 * LSP: Bind to stdout as a server for Language Server Protocol (LSP). This mode is for IDE support.
 *
 * TUI: The text based user interface (TUI) is an interactive console that allows exploring the analyzed source code by manual queries.
 */
class ExecutionMode {
	@Option(names = "-c", required = true, description = "Start in command line mode.")
	boolean cli;
	@Option(names = "-l", required = true, description = "Start in language server protocol (LSP) mode.")
	boolean lsp;
	@Option(names = "-t", required = true, description = "Start interactive console (Text-based User Interface).")
	boolean tui;
}

class AnalysisMode {

	@Option(names = "--typestate", paramLabel = "<NFA|WPDS>", defaultValue = "NFA", type = TypestateMode.class, description = "Typestate analysis mode\nNFA:  Non-deterministic finite automaton (faster, intraprocedural)\nWPDS: Weighted pushdown system (slower, interprocedural)")
	//@CommandLine.ArgGroup(exclusive = true, multiplicity = "1", heading = "Typestate Analysis\n")
	protected TypestateMode tsMode = TypestateMode.NFA;
}

class TranslationSettings {
	@Option(names = {
			"--analyze-includes" }, description = "Enables parsing of include files. By default, if --includes are given, the parser will resolve symbols/templates from these include, but not load their parse tree.")
	protected boolean analyzeIncludes = false;

	@Option(names = { "--includes" }, description = "Path(s) containing include files. Path must be separated by : (Mac/Linux) or ; (Windows)", split = ":|;")
	protected File[] includesPath;
}
