# Error reports

This document tells how to analyze the exception reports and the freeze reports that users send through JetBrains Marketplace.

## 1. How the reports get to the maintainer

`plugin.xml` registers the error handler `JetBrainsMarketplaceErrorReportSubmitter` of the IntelliJ Platform.
When the plugin causes an exception, the **IDE Internal Errors** dialog shows **Report to third-party plugin**.
The user can click this button to send the report.
If the setting **Send error reports to JetBrains automatically** is on, the IDE sends the reports automatically.
The IDE sends a report only if the user agrees.

JetBrains Marketplace shows the reports on the plugin page:

- The **Exceptions** tab shows the exception reports.
- The **Freezes** tab shows the reports of UI freezes.

An exception report contains the stack trace, the IDE version, and the plugin version.
It can also contain a comment from the user.

A freeze report contains these items:

- A part of a thread dump.
- The full thread dumps (`dump-1.txt`, `dump-2.txt`, and the other dump files).

The freeze report can also contain `snapshot.jfr` (a Java Flight Recorder snapshot), `report.txt`, and `open-telemetry-metrics.csv`.

**NOTE:** The plugin does not operate in split mode at this time.
If the plugin gets a frontend module later, register the error handler in the frontend module or in a shared module.
If you register the error handler only in a backend module, the error handler does not operate.

## 2. Analyze an exception report

1. Open the **Exceptions** tab on the plugin page in JetBrains Marketplace.
2. Open the report. Copy the stack trace.
3. Open this repository in IntelliJ IDEA.
4. Check out the tag of the plugin version from the report.
5. Select **Code | Analyze Stack Trace or Thread Dump**.
6. Paste the stack trace. Click **OK**.
7. Find the first line of the stack trace that starts with `io.github.artptr.moonexplorer`.
8. Click the link in this line to open the source code.
9. Find the cause of the exception.
10. Find an issue for this exception on GitHub. If there is no issue, create an issue with the label `area/diagnostics`.
11. Put the stack trace, the IDE version, and the plugin version in the issue.

**NOTE:** Do not copy personal data from the report into the issue, for example file paths with user names.

## 3. Analyze a freeze report

A UI freeze occurs when code blocks the event dispatch thread (EDT) for a long time.
The thread dumps show the EDT at the times of the freeze.

1. Open the **Freezes** tab on the plugin page in JetBrains Marketplace.
2. Open the report. Read the part of the thread dump.
3. Download the full thread dumps (`dump-N.txt`).
4. Open this repository in IntelliJ IDEA.
5. Check out the tag of the plugin version from the report.
6. Select **Code | Analyze Stack Trace or Thread Dump**.
7. Paste the text of `dump-1.txt`. Click **OK**.
8. Find the thread `AWT-EventQueue`. This thread is the EDT.
9. In the stack of the EDT, find the lines that start with `io.github.artptr.moonexplorer`.
10. Do steps 7 to 9 again for each other dump file.
11. Compare the stacks of the EDT in the dumps.

If the same plugin code is in the stack of the EDT in all dumps, this code is the probable cause of the freeze.
Typical causes are a moon CLI call, file input/output, or a long calculation on the EDT.
Code rule 5 in [AGENTS.md](../AGENTS.md) does not let you do these operations on the EDT.
Move the work to a background thread. Refer to the threading rules in the skill `intellij-plugin-dev`.

If the EDT waits for a lock, find the thread that has the lock.
That thread is the cause of the freeze.

For more data, open `snapshot.jfr` in a profiler, for example the profiler of IntelliJ IDEA.

Find an issue for this freeze on GitHub. If there is no issue, create an issue with the label `area/diagnostics`.
Put the stack of the EDT, the IDE version, and the plugin version in the issue.

**NOTE:** JetBrains supplies the AI agent skill `analyze-freeze` for the analysis of freeze reports.
Refer to [UI freeze reports are now available in JetBrains Marketplace](https://blog.jetbrains.com/platform/2026/10/ui-freeze-reports-in-marketplace/).

## 4. References

- [Error handler, JetBrains Marketplace documentation](https://plugins.jetbrains.com/docs/marketplace/error-handler.html)
- [UI freeze reports are now available in JetBrains Marketplace](https://blog.jetbrains.com/platform/2026/10/ui-freeze-reports-in-marketplace/)
- [Threading model, IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/threading-model.html)
