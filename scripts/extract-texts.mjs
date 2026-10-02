#!/usr/bin/env node
/*
  Writes the server's English catalogue of television texts — `BE/src/main/resources/translations/en/tv.json`
  — out of the application's own sources.

  ⚠️ THE CATALOGUE IS NEVER EDITED BY HAND. Every text on the television is written once, at its call site,
  as `t("tv.home.continue", "Continue watching")` or `plural("tv.count.titles", count, "{count} title",
  "{count} titles")`; this script reads those calls and writes every key with its English, sorted. The
  server's Translations page lists only the keys this file holds, so a text missing here is a text nobody
  can translate.

  ⚠️ IT REFUSES rather than guesses:
    - a call whose key or English is not a plain string literal (a variable, a `$template`, a `+`);
    - one key written with two different English texts.

    node scripts/extract-texts.mjs            write the catalogue
    node scripts/extract-texts.mjs --check    exit 1 when the catalogue on disk is not what the sources say
*/

import { readdirSync, readFileSync, writeFileSync, statSync } from "node:fs";
import { join, relative, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const televisionRoot = join(here, "..");
const sourceRoot = join(televisionRoot, "app", "src", "main", "java");
const catalogue = join(televisionRoot, "..", "BE", "src", "main", "resources", "translations", "en", "tv.json");

/** The file that defines `t` and `plural` — its KDoc shows calls that are examples, not texts. */
const DEFINITIONS = join("net", "innoventa", "kitsu", "tv", "text", "Texts.kt");

function kotlinSources(directory) {
    return readdirSync(directory).flatMap((name) => {
        const path = join(directory, name);
        if (statSync(path).isDirectory()) {
            return kotlinSources(path);
        }
        return name.endsWith(".kt") ? [path] : [];
    });
}

/**
 * The source with every comment blanked out, keeping offsets and line numbers — so a `t(` quoted in a
 * comment is not read as a call, and a `//` inside a string is not read as a comment.
 */
function withoutComments(source) {
    const characters = source.split("");
    let index = 0;
    while (index < characters.length) {
        const current = characters[index];
        const next = characters[index + 1];
        if (current === '"') {
            index = skipString(source, index);
            continue;
        }
        if (current === "'") {
            index = skipCharacter(source, index);
            continue;
        }
        if (current === "/" && next === "/") {
            while (index < characters.length && characters[index] !== "\n") {
                characters[index] = " ";
                index++;
            }
            continue;
        }
        if (current === "/" && next === "*") {
            let depth = 0;
            do {
                if (characters[index] === "/" && characters[index + 1] === "*") {
                    depth++;
                    characters[index] = " ";
                    characters[index + 1] = " ";
                    index += 2;
                } else if (characters[index] === "*" && characters[index + 1] === "/") {
                    depth--;
                    characters[index] = " ";
                    characters[index + 1] = " ";
                    index += 2;
                } else {
                    if (characters[index] !== "\n") {
                        characters[index] = " ";
                    }
                    index++;
                }
            } while (depth > 0 && index < characters.length);
            continue;
        }
        index++;
    }
    return characters.join("");
}

/** The index just past the string literal starting at [start] — raw `"""` strings included. */
function skipString(source, start) {
    if (source.startsWith('"""', start)) {
        const end = source.indexOf('"""', start + 3);
        return end < 0 ? source.length : end + 3;
    }
    let index = start + 1;
    while (index < source.length) {
        const current = source[index];
        if (current === "\\") {
            index += 2;
            continue;
        }
        if (current === "$" && source[index + 1] === "{") {
            index = skipBalanced(source, index + 1, "{", "}");
            continue;
        }
        if (current === '"') {
            return index + 1;
        }
        index++;
    }
    return index;
}

function skipCharacter(source, start) {
    let index = start + 1;
    if (source[index] === "\\") {
        index += 2;
    } else {
        index += 1;
    }
    while (index < source.length && source[index] !== "'") {
        index++;
    }
    return index + 1;
}

/** The index just past the bracket that closes the one at [start]. */
function skipBalanced(source, start, open, close) {
    let depth = 0;
    let index = start;
    while (index < source.length) {
        const current = source[index];
        if (current === '"') {
            index = skipString(source, index);
            continue;
        }
        if (current === "'") {
            index = skipCharacter(source, index);
            continue;
        }
        if (current === open) {
            depth++;
        } else if (current === close) {
            depth--;
            if (depth === 0) {
                return index + 1;
            }
        }
        index++;
    }
    return index;
}

/** The arguments of the call whose `(` is at [open], split at its top-level commas, as source text. */
function argumentsOf(source, open) {
    const end = skipBalanced(source, open, "(", ")");
    const body = source.slice(open + 1, end - 1);
    const parts = [];
    let depth = 0;
    let from = 0;
    let index = 0;
    while (index < body.length) {
        const current = body[index];
        if (current === '"') {
            index = skipString(body, index);
            continue;
        }
        if (current === "'") {
            index = skipCharacter(body, index);
            continue;
        }
        if ("([{".includes(current)) {
            depth++;
        } else if (")]}".includes(current)) {
            depth--;
        } else if (current === "," && depth === 0) {
            parts.push(body.slice(from, index).trim());
            from = index + 1;
        }
        index++;
    }
    const last = body.slice(from).trim();
    if (last.length > 0) {
        parts.push(last);
    }
    return parts;
}

/** The value of a plain Kotlin string literal, or null when the text is anything else. */
function literal(text) {
    const match = /^"((?:[^"\\$]|\\.)*)"$/s.exec(text);
    if (!match) {
        return null;
    }
    return match[1].replace(/\\(u[0-9a-fA-F]{4}|.)/g, (escape, code) => {
        if (code.startsWith("u") && code.length === 5) {
            return String.fromCharCode(parseInt(code.slice(1), 16));
        }
        return { n: "\n", t: "\t", r: "\r", b: "\b", '"': '"', "'": "'", "\\": "\\", $: "$" }[code] ?? escape;
    });
}

const texts = new Map();
const problems = [];

function record(key, english, where) {
    const known = texts.get(key);
    if (known !== undefined && known.english !== english) {
        problems.push(`${where}: "${key}" is "${english}" here and "${known.english}" at ${known.where}`);
        return;
    }
    if (known === undefined) {
        texts.set(key, { english, where });
    }
}

for (const file of kotlinSources(sourceRoot)) {
    const name = relative(sourceRoot, file);
    if (name === DEFINITIONS) {
        continue;
    }
    const source = withoutComments(readFileSync(file, "utf8"));
    const calls = /(?<![\w.])(fun\s+)?(t|plural)\s*\(/g;
    let match;
    while ((match = calls.exec(source)) !== null) {
        if (match[1]) {
            continue;
        }
        const function_ = match[2];
        const open = match.index + match[0].length - 1;
        const line = source.slice(0, open).split("\n").length;
        const where = `${name.replaceAll("\\", "/")}:${line}`;
        const parts = argumentsOf(source, open);
        const key = literal(parts[0] ?? "");

        if (function_ === "t") {
            const english = literal(parts[1] ?? "");
            if (key === null || english === null) {
                problems.push(`${where}: t(${parts.slice(0, 2).join(", ")}) — the key and the English must be plain string literals`);
                continue;
            }
            record(key, english, where);
        } else {
            const one = literal(parts[2] ?? "");
            const other = literal(parts[3] ?? "");
            if (key === null || one === null || other === null) {
                problems.push(`${where}: plural(${parts.slice(0, 4).join(", ")}) — the key and both English forms must be plain string literals`);
                continue;
            }
            record(`${key}.one`, one, where);
            record(`${key}.other`, other, where);
        }
        if (key !== null && !key.startsWith("tv.")) {
            problems.push(`${where}: "${key}" — every television key starts with "tv."`);
        }
    }
}

if (problems.length > 0) {
    console.error(`extract-texts: ${problems.length} problem(s), nothing written\n`);
    problems.forEach((problem) => console.error(`  ${problem}`));
    process.exit(1);
}

const sorted = Object.fromEntries([...texts.keys()].sort().map((key) => [key, texts.get(key).english]));
const written = JSON.stringify(sorted, null, 2) + "\n";

if (process.argv.includes("--check")) {
    const current = readFileSync(catalogue, "utf8").replace(/\r\n/g, "\n");
    if (current !== written) {
        console.error(`extract-texts: ${relative(process.cwd(), catalogue)} is out of date — run without --check`);
        process.exit(1);
    }
    console.log(`extract-texts: ${texts.size} keys, catalogue up to date`);
} else {
    writeFileSync(catalogue, written, "utf8");
    console.log(`extract-texts: ${texts.size} keys → ${relative(process.cwd(), catalogue)}`);
}
