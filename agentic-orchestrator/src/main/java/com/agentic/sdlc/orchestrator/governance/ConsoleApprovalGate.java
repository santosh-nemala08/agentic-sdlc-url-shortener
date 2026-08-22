package com.agentic.sdlc.orchestrator.governance;

import com.agentic.sdlc.orchestrator.execution.WorkflowContext;
import com.agentic.sdlc.orchestrator.graph.StageId;

import java.util.Locale;
import java.util.Scanner;

/**
 * Blocks on stdin for a real y/n human decision. This is what "human
 * approval checkpoints for high-impact actions" looks like when there is
 * actually someone at the keyboard, as opposed to {@link AutoApprovalGate}
 * which exercises the same code path unattended.
 */
public final class ConsoleApprovalGate implements ApprovalGate {

    private final Scanner scanner;

    public ConsoleApprovalGate() {
        this(new Scanner(System.in));
    }

    public ConsoleApprovalGate(Scanner scanner) {
        this.scanner = scanner;
    }

    @Override
    public ApprovalDecision requestApproval(WorkflowContext context, StageId stageId, String description) {
        System.out.printf("%n[APPROVAL REQUIRED] Stage '%s' -- %s%nApprove? (y/n): ", stageId, description);
        String line = scanner.hasNextLine() ? scanner.nextLine().trim().toLowerCase(Locale.ROOT) : "n";
        ApprovalDecision decision = line.startsWith("y") ? ApprovalDecision.APPROVED : ApprovalDecision.REJECTED;
        System.out.println("-> " + decision);
        return decision;
    }
}
