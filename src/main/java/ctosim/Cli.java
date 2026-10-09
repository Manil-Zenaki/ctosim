package ctosim;

import java.io.PrintStream;

/** The ctosim command: XLSForm to simulated SurveyCTO exports. See specs/10-cli.md. */
public final class Cli {

    private Cli() {
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }
}
