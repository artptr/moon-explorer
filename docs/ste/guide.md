# STE guide for this project

This project writes technical documentation in ASD-STE100 Simplified Technical English (STE).
STE is a controlled language. It makes text clear for readers whose first language is not English.
It also makes text clear for AI agents and translation tools.

The specification is ASD-STE100, Issue 9. Refer to <https://www.asd-ste100.org>.

## 1. Scope

STE applies to these texts:

- All Markdown files, except the files in the [allowlist](allowlist.md)
- Code comments and KDoc
- Commit messages
- Issue and pull request text
- User interface text in `MoonExplorerBundle.properties`

## 2. Words

1. Use the approved words of the STE dictionary.
2. Use each approved word only with its approved meaning and part of speech.
3. You can use technical names and technical verbs. These are words that are specific to this subject.
4. Write technical names in their usual form. Examples: `moon`, `IntelliJ IDEA`, `Gradle`, `tool window`, `pull request`.
5. Use one term for one thing. This project uses these terms:

| Term | Meaning | Do not use |
|------|---------|------------|
| moon | The moon build system | Moon, moonrepo (except in URLs) |
| workspace | A directory that contains a `.moon/` directory | monorepo, repo (for the moon workspace) |
| project | A moon project in a workspace | package, module |
| task | A moon task in a project | target, script, job |
| IDE | An IntelliJ-based IDE | editor, IntelliJ (for all IDEs) |
| tool window | A panel of the IDE | pane, sidebar |
| plugin | Moon Explorer or another IntelliJ Platform plugin | extension, add-on |

## 3. Sentences

1. Write procedural sentences with a maximum of 20 words.
2. Write descriptive sentences with a maximum of 25 words.
3. Write only one instruction in each sentence.
4. Use the active voice.
5. Use the imperative for instructions.
6. Use only the simple present, simple past, and simple future tense.
7. Do not use contractions.

## 4. Paragraphs and structure

1. Write one topic in each paragraph.
2. Use a maximum of six sentences in a paragraph.
3. Use numbered lists for procedures.
4. Write notes, cautions, and warnings as separate paragraphs:
   - **WARNING:** Risk of injury or loss of data.
   - **CAUTION:** Risk of damage to equipment or to the project.
   - **NOTE:** Information that helps the reader.

## 5. Automatic checks

The Vale tool checks some STE rules. The rules are in `.vale/styles/STE/`.

1. Install Vale. Refer to <https://vale.sh/docs/install>.
2. Run `vale .` from the root directory of the repository.
3. Correct all errors.

The `Docs` workflow runs the same check on each pull request.

Vale examines the Markdown files and the comments in `.github/workflows/*.yml`. Vale does not examine the YAML keys and values.

**NOTE:** Vale cannot find all comments in a shell script in a `run` key. Vale also does not examine the comments in other YAML files. A person or the `ste-reviewer` subagent must do a review of these comments.

**NOTE:** Vale checks all sentences against the descriptive limit of 25 words. It cannot find procedural sentences of 21 to 25 words. Vale also cannot find all other STE problems. For example, it cannot find an approved word with a meaning that is not approved. Use the `ste-reviewer` subagent for a full review.
