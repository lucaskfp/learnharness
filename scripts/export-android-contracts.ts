/**
 * Export the tool contracts the Android app imports (android/app/src/main/assets/upstream/tools.json).
 *
 * The Android runtime does not retype upstream's tool names, descriptions or input schemas: it
 * reads this file, which is generated from the real tool modules. Prompts are copied by the
 * Gradle task `syncUpstreamPrompts` instead. Refresh after changing a tool contract upstream:
 *
 *   npx tsx scripts/export-android-contracts.ts
 *
 * `tests/android-contracts.test.ts` fails when the committed file is stale.
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { askUserQuestionTool, probeExplainTool, probeQuizTool, teachExplainToolFor, teachQuizToolFor } from "../src/agent/tools.ts";
import { probeDoneTool } from "../src/agent/tools/probe.ts";
import { planTools, PLAN_NODE_IDS_REMINDER } from "../src/agent/tools/plan.ts";
import { HANDOFF_TEXT, PROBE_DONE_ACK_TEXT } from "../src/core/contracts.ts";
import { questionResult } from "../src/agent/tool-execute.ts";
import { INTERRUPTED_TEXT } from "../src/core/native-messages.ts";

const HERE = dirname(fileURLToPath(import.meta.url));
export const OUTPUT = join(HERE, "..", "android", "app", "src", "main", "assets", "upstream", "tools.json");

/** The JSON the Android app reads. Plain data, so a diff shows exactly what changed upstream. */
// The teacher-side tools are built per request over a chat store; the contract (name, description,
// schema) does not depend on it, so a store that holds nothing is enough to read them.
const NO_CHAT = { chatId: "contract", chatStore: { getChat: () => undefined } as never };

export function androidContracts(): unknown {
	const tool = (t: { name: string; description: string; parameters: unknown }) => ({
		name: t.name,
		description: t.description,
		inputSchema: JSON.parse(JSON.stringify(t.parameters)),
	});
	return {
		// Source of each tool in src/agent/tools.ts, for whoever refreshes this file.
		tools: [
				tool(probeQuizTool),
				tool(probeExplainTool),
				tool(askUserQuestionTool),
				tool(probeDoneTool({ ...NO_CHAT, deliver: () => {} }).probe_done!),
				tool(planTools(NO_CHAT).plan_init!),
				tool(teachQuizToolFor(NO_CHAT)),
				tool(teachExplainToolFor(NO_CHAT)),
			],
		texts: {
			questionPosed: questionResult().content[0]!.type === "text" ? (questionResult().content[0] as { text: string }).text : "",
			interrupted: INTERRUPTED_TEXT,
				probeDoneAck: PROBE_DONE_ACK_TEXT,
				probeHandoff: HANDOFF_TEXT,
				planNodeIdsReminder: PLAN_NODE_IDS_REMINDER,
		},
	};
}

export function renderAndroidContracts(): string {
	return JSON.stringify(androidContracts(), null, 2) + "\n";
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
	mkdirSync(dirname(OUTPUT), { recursive: true });
	writeFileSync(OUTPUT, renderAndroidContracts());
	console.log(`wrote ${OUTPUT}`);
}
