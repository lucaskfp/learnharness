/**
 * The Android app imports tool contracts from a committed JSON file. This pins that file to the
 * real tool modules: change a contract upstream and this fails until
 * `npx tsx scripts/export-android-contracts.ts` is re-run.
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { OUTPUT, renderAndroidContracts } from "../scripts/export-android-contracts.ts";

test("android tool contracts are up to date", () => {
	assert.equal(readFileSync(OUTPUT, "utf8"), renderAndroidContracts());
});
