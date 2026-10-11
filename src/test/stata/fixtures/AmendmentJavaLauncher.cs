using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text;

// Test-only Java launcher: advertises a controlled version, records -jar
// invocations, and forwards simulation to the installed Java 17+ runtime.
public static class AmendmentJavaLauncher
{
    private static string Quote(string value)
    {
        var result = new StringBuilder("\"");
        int slashes = 0;
        foreach (char c in value)
        {
            if (c == '\\') { slashes++; continue; }
            result.Append('\\', c == '"' ? slashes * 2 + 1 : slashes);
            result.Append(c);
            slashes = 0;
        }
        result.Append('\\', slashes * 2);
        return result.Append('"').ToString();
    }

    public static int Main(string[] args)
    {
        string[] settings = File.ReadAllLines(Assembly.GetExecutingAssembly().Location + ".settings");
        string version = settings[0], realJava = settings[1], work = settings[2], slot = settings[3];
        if (args.Contains("-version") || args.Contains("--version"))
        {
            TextWriter output = settings[4] == "stdout" ? Console.Out : Console.Error;
            if (args.Contains("--version")) output.WriteLine("openjdk " + version + " 2026-01-20");
            else output.WriteLine("openjdk version \"" + version + "\" 2026-01-20");
            output.WriteLine("OpenJDK Runtime Environment (test launcher)");
            output.WriteLine("OpenJDK 64-Bit Server VM (test launcher)");
            return 0;
        }
        if (args.Contains("-jar"))
            File.AppendAllText(Path.Combine(work, "launched-" + slot + ".txt"), "invoked\n");
        var start = new ProcessStartInfo(realJava, string.Join(" ", args.Select(Quote).ToArray()));
        start.UseShellExecute = false;
        start.CreateNoWindow = true;
        start.RedirectStandardOutput = true;
        start.RedirectStandardError = true;
        using (Process process = Process.Start(start))
        {
            var stderr = process.StandardError.ReadToEndAsync();
            Console.Out.Write(process.StandardOutput.ReadToEnd());
            process.WaitForExit();
            Console.Error.Write(stderr.Result);
            return process.ExitCode;
        }
    }
}
