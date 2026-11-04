#!/usr/bin/env node
/**
 * List string resources in values/strings.xml that nothing appears to reference.
 *
 * Usage: node find_unused_strings.cjs <module_dir> [<extra_search_dir> ...]
 *
 * Looks for R.string.name, @string/name and getString-by-name across source
 * and resource files. This is a heuristic: names built at runtime (string
 * concatenation, reflection, data binding expressions) cannot be detected, so
 * treat the output as candidates to review rather than a delete list.
 */

const fs = require('fs');
const path = require('path');
const { readStrings, readPlurals, escapeRegExp } = require('./res.cjs');

const CODE_EXT = new Set(['.kt', '.java', '.xml', '.kts', '.js', '.ts']);

function walk(dir, files = []) {
    let entries;
    try {
        entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
        return files;
    }
    for (const e of entries) {
        const full = path.join(dir, e.name);
        if (e.isDirectory()) {
            if (e.name === 'build' || e.name === '.git' || e.name === 'node_modules' || e.name === '.gradle') continue;
            walk(full, files);
        } else if (CODE_EXT.has(path.extname(e.name)) && e.name !== 'lint-baseline.xml') {
            files.push(full);
        }
    }
    return files;
}

// Parse args: node find_unused_strings.cjs <module_dir> [<extra_search_dir> ...] [--file <f>] [--delete]
const rawArgs = process.argv.slice(2);
let targetFile = null;
let doDelete = false;
const searchDirs = [];

for (let i = 0; i < rawArgs.length; i++) {
    if (rawArgs[i] === '--file' && i + 1 < rawArgs.length) {
        targetFile = rawArgs[++i];
    } else if (rawArgs[i] === '--delete') {
        doDelete = true;
    } else if (!rawArgs[i].startsWith('--')) {
        searchDirs.push(rawArgs[i]);
    }
}

if (searchDirs.length < 1) {
    console.log('Usage: node find_unused_strings.cjs <module_dir> [<extra_search_dir> ...] [--file <res_file>] [--delete]');
    process.exit(1);
}

const moduleDir = searchDirs[0];
const valuesDir = path.join(moduleDir, 'src', 'main', 'res', 'values');

if (!fs.existsSync(valuesDir)) {
    console.error(`Values directory not found: ${valuesDir}`);
    process.exit(1);
}

const stringFiles = targetFile
    ? [path.join(valuesDir, targetFile)]
    : fs.readdirSync(valuesDir)
        .filter(f => f.startsWith('strings') && f.endsWith('.xml'))
        .sort()
        .map(f => path.join(valuesDir, f));

if (stringFiles.length === 0) {
    console.error(`No string resource files found in ${valuesDir}`);
    process.exit(1);
}

// Collect all strings & plurals to inspect
const allCandidates = [];
for (const sf of stringFiles) {
    if (!fs.existsSync(sf)) continue;
    const baseName = path.basename(sf);
    const strings = readStrings(sf);
    for (const [name] of strings.entries()) {
        allCandidates.push({ name, type: 'string', file: sf, baseName });
    }
    const plurals = readPlurals(sf);
    for (const [name] of plurals.entries()) {
        allCandidates.push({ name, type: 'plurals', file: sf, baseName });
    }
}

if (allCandidates.length === 0) {
    console.error('No string or plural declarations found.');
    process.exit(1);
}

// Search all specified directories
const allFiles = searchDirs.flatMap(d => walk(d));

// Build code/layout haystack (exclude values*/ XMLs to avoid matching declaration strings)
const nonValuesFiles = allFiles.filter(f => !f.includes('/res/values'));
const codeHaystack = nonValuesFiles.map(f => fs.readFileSync(f, 'utf8')).join('\n');

// Also include non-localized values files (e.g. themes.xml, arrays.xml, styles.xml) that might reference @string
const nonLocalizedValuesFiles = allFiles.filter(f => f.includes('/res/values/') && !f.includes('/res/values-'));
const valuesHaystack = nonLocalizedValuesFiles
    .filter(f => !stringFiles.some(sf => path.resolve(sf) === path.resolve(f)))
    .map(f => fs.readFileSync(f, 'utf8'))
    .join('\n');

const fullHaystack = `${codeHaystack}\n${valuesHaystack}`;

const unused = allCandidates.filter(item => {
    const re = new RegExp(`(R\\.${item.type}\\.${escapeRegExp(item.name)}|@${item.type}/${escapeRegExp(item.name)})\\b`);
    return !re.test(fullHaystack);
});

if (unused.length === 0) {
    console.log(`All ${allCandidates.length} strings/plurals in ${stringFiles.map(f => path.basename(f)).join(', ')} are referenced.`);
    process.exit(0);
}

console.log(`${unused.length} of ${allCandidates.length} strings look unused:\n`);

// Group by source file
const byFile = new Map();
for (const u of unused) {
    if (!byFile.has(u.baseName)) byFile.set(u.baseName, []);
    byFile.get(u.baseName).push(u.name);
}

for (const [file, names] of byFile.entries()) {
    console.log(`=== ${file} (${names.length}) ===`);
    names.forEach(n => console.log(`  ${n}`));
}

if (!doDelete) {
    console.log('\nHeuristic only — check for runtime-built names before deleting.');
    console.log('Run with --delete to remove these unused strings from values/ and all values-*/ directories.');
    process.exit(0);
}

console.log('\n--- Deleting unused strings across all locales ---');
const resDir = path.join(moduleDir, 'src', 'main', 'res');
const valDirs = fs.readdirSync(resDir).filter(d => d === 'values' || d.startsWith('values-'));

let totalDeleted = 0;
for (const [baseName, names] of byFile.entries()) {
    const namesSet = new Set(names);
    const tagRegex = /^[ \t]*<(?:string|plurals)\b[^>]*\bname="([^"]+)"[^>]*>[\s\S]*?<\/(?:string|plurals)>[ \t]*(?:\r?\n)?/gm;

    for (const vd of valDirs) {
        const filePath = path.join(resDir, vd, baseName);
        if (!fs.existsSync(filePath)) continue;

        const xml = fs.readFileSync(filePath, 'utf8');
        let countInFile = 0;
        const cleaned = xml.replace(tagRegex, (match, name) => {
            if (namesSet.has(name)) {
                countInFile++;
                return '';
            }
            return match;
        });

        if (countInFile > 0) {
            fs.writeFileSync(filePath, cleaned, 'utf8');
            totalDeleted += countInFile;
            console.log(`Removed ${countInFile} unused strings from ${vd}/${baseName}`);
        }
    }
}

console.log(`\nSuccessfully deleted ${totalDeleted} entries across all locale directories.`);
