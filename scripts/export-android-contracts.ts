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
import { askUserQuestionTool, probeExplainTool, probeQuizTool } from "../src/agent/tools.ts";
import { questionResult } from "../src/agent/tool-execute.ts";
import { INTERRUPTED_TEXT } from "../src/core/native-messages.ts";

const HERE = dirname(fileURLToPath(import.meta.url));
export const OUTPUT = join(HERE, "..", "android", "app", "src", "main", "assets", "upstream", "tools.json");

/** The JSON the Android app reads. Plain data, so a diff shows exactly what changed upstream. */
export function androidContracts(): unknown {
	const tool = (t: { name: string; description: string; parameters: unknown }) => ({
		name: t.name,
		description: t.description,
		inputSchema: JSON.parse(JSON.stringify(t.parameters)),
	});
	return {
		// Source of each tool in src/agent/tools.ts, for whoever refreshes this file.
		tools: [tool(probeQuizTool), tool(probeExplainTool), tool(askUserQuestionTool)],
		texts: {
			questionPosed: questionResult().content[0]!.type === "text" ? (questionResult().content[0] as { text: string }).text : "",
			interrupted: INTERRUPTED_TEXT,
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
