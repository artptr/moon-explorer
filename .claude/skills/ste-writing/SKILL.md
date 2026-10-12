---
name: ste-writing
description: Write and edit technical documentation in ASD-STE100 Simplified Technical English (STE). Use when you write or change Markdown documentation, code comments, commit messages, issue text, or pull request text in this repository. Do not use for files in the STE allowlist (README.md).
---

# Write in Simplified Technical English

All technical documentation in this repository uses ASD-STE100 Simplified Technical English (STE).
The full project rules are in [docs/ste/guide.md](../../../docs/ste/guide.md).

## Step 1: Do a check of the allowlist

Read [docs/ste/allowlist.md](../../../docs/ste/allowlist.md).
If the file is in the allowlist, write in standard English and stop here.

## Step 2: Write the text

Obey these rules:

1. Use only approved words with their approved meaning. Refer to the substitution table below.
2. Use technical names and technical verbs from this project without change. Examples: `plugin`, `tool window`, `Gradle`, `moon`, `compile`, `commit`.
3. Use one word for one meaning. Do not use synonyms for the same thing.
4. Write procedural sentences with a maximum of 20 words.
5. Write descriptive sentences with a maximum of 25 words.
6. Write only one instruction in each sentence. You can write two actions in one sentence only if they occur at the same time.
7. Use the imperative (command) form for instructions. Example: "Run the tests."
8. Use the active voice. Do not use the passive voice in instructions.
9. Use the simple tenses: present, past, and future. Do not use "-ing" forms as verbs.
10. Use a maximum of six sentences in a paragraph.
11. Write notes, cautions, and warnings as separate paragraphs. Start them with **NOTE:**, **CAUTION:**, or **WARNING:**.
12. Use numbered lists for procedures. Use bulleted lists for items that have no sequence.
13. Do not use contractions. Write `do not`, not `don't`.

## Substitution table

| Do not use | Use |
|------------|-----|
| `ensure` | make sure |
| `utilize`, `leverage` | use |
| `should` | must (rule) or use the imperative |
| `please` | (delete the word) |
| `via` | through, with |
| `require` | must have, be necessary |
| `allow`, `enable (= permit)` | let |
| `perform` | do |
| `obtain`, `fetch` | get |
| `additional` | more |
| `prior to` | before |
| `in order to` | to |
| `easy`, `easily` | (delete the word or be specific) |
| `e.g.` | for example |
| `i.e.` | that is |
| `etc.` | (give the full list) |
| `appropriate` | correct, applicable |
| `facilitate` | help |
| `verify` | make sure, do a check of |

The Vale style in `.vale/styles/STE/` contains more substitutions.

## Step 3: Do a check of the text

1. Run `vale <file>` for each changed file.
2. Correct all errors. Correct warnings where possible.
3. For long or important changes, start the subagent `ste-reviewer`.

## Examples

Not STE:

```text
You should ensure that the IDE is restarted after the plugin has been installed, otherwise it won't work.
```

STE:

```text
Install the plugin. Then restart the IDE.

NOTE: The plugin does not operate until you restart the IDE.
```
