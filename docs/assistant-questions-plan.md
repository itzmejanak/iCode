# The assistant asks the user — plan

Status: Q1–Q5 done in code (2026-11-09); the phone checks in §6 are the owner's. Owner's request: the assistant has gaps compared with Claude Code's
AskUserQuestion — "structured dialogue: instead of guessing or writing plain text that might get
lost in a long response, Claude pauses its task" and asks "a direct question along with a set of
options (with labels and descriptions) or open-ended prompts" — with a sound architecture, a clear
scope and boundary, no future issues, scalable, stable, no duplication and no inconsistency.
Follows [backend-split-rules.md](./backend-split-rules.md), [assistant-koog-plan.md](./assistant-koog-plan.md),
[assistant-permissions-plan.md](./assistant-permissions-plan.md) and
[assistant-sheets-plan.md](./assistant-sheets-plan.md).

## 1. The reference

Claude Code's `AskUserQuestion` ([Anthropic, Handle approvals and user input](https://code.claude.com/docs/en/agent-sdk/user-input)):

- A tool the model calls when a task has more than one valid approach. The turn waits until the
  answer comes back, with no time limit.
- One call holds 1–4 questions. Each has the question, a `header` of at most 12 characters, 2–4
  options with a `label` and a `description`, and `multiSelect`.
- The user can always answer in their own words: an "Other" choice after the model's options. The
  answer is their text, not the word "Other".
- The answer goes back as each question with its chosen labels (several joined for multi-select).
  An optional free reply (`response`) can replace the answers when the user dismisses the questions
  and writes something else.
- It is common in plan mode, where the model explores and asks before proposing.

(Content was rephrased for compliance with licensing restrictions.)

## 2. Measured (code, 2026-11-09)

- **The model can only ask in prose.** The instructions say "tell the user what is missing and ask"
  (Code) and "Ask clarifying questions" (Plan). The question ends the turn inside a long answer,
  and the user's reply starts a new turn without the context of the tool calls in hand.
- **The turn already waits for the user in two ways**, both owned by
  `data/assistant/AssistantQuestions.kt`:
  - an approval before a call (`AssistantApproval`, answered by `ICodeIntent.AnswerAssistant`);
  - a pause between steps (`AssistantPause`: step limit and phone hand-over, answered by
    `ContinueAssistant`).

  Each is a `CompletableDeferred` slot, published to `AssistantState`, cleared in `finally`, and
  ended by Stop. The approval and pause code repeat the same publish, await and clear steps.
- **An MCP server's question** (elicitation, `McpQuestions`, `McpQuestionCard`) is the one form iCode
  shows today. Its wait is driven by the MCP SDK, and its fields are the MCP schema's (text kinds,
  flag, choice, choices). Its options have no description. Its multi-select checkbox is not part of
  the row's touch target, which is an accessibility gap.
- **Every surface that shows "the turn waits for you" reads approval and pause itself:**
  - the step status (`AssistantRules.entries`, Asking);
  - the cards above the composer (`AssistantChatView`);
  - the step sheet's footer (`AssistantScreen`);
  - the background notification (`BackgroundWorkRules.assistant`);
  - the phone bubble (`PhoneOverlay.of`, `PhoneBubbleBar.Question`);
  - the spoken turn (`SpokenTurn.asked`).

  A new kind of question must reach each of them, or it shows as a call that hangs.
- **The composer cannot answer.** Send is disabled while any turn runs. A voice message heard while
  a turn runs waits in the field, and no voice path answers a question.
- **A retry starts over.** A transient provider failure re-runs `engine.run` with the request built
  before the turn's steps. The model then repeats the calls it already made, writes included, and
  would ask a question it already had an answer to.
- **Nothing pending is saved.** A process killed mid-turn closes open calls as "Stopped before it
  ran" (`AssistantRules.closed`). This applies to approvals and would apply to questions.

## 3. Decisions

**D1 — A tool, `ask_user`.** The model calls it as any other tool, and its call is a step like the
others. Rejected: asking in prose, which is what exists, and ending the turn to ask, which loses the
turn's place. Its parameters follow Claude Code's, so models that know that tool use this one
without being taught: `questions`, each with `question`, `header`, `options` (`label`,
`description`) and `multiSelect`.

**D2 — One owner of what the turn asks.** The questions are a third slot of `AssistantQuestions`,
beside approval and pause. They are not a new holder class, and they do not reuse MCP's wait, which
belongs to the MCP SDK. The slot's publish, await and clear steps are written once and shared by
all three slots. `AssistantQuestions` is given to `AssistantTurns` by the coordinator. The
coordinator routes answers to it directly, so the turn runner only runs turns.

**D3 — The domain model** (`domain/AssistantClarification.kt`):
- `AssistantClarification(call, questions)` is what the turn asks, published as
  `AssistantState.clarification`.
- `AssistantQuestion(header, text, options, multiple)` holds `Option(label, description)` and
  `Answer(chosen, own)`.
- `AssistantReply` is one of `Answered(answers)`, `Said(text)` (the user's own words instead of the
  questions) or `Skipped`.
- `AssistantQuestionRules` holds the limits (1–4 questions, 2–4 options, a header of at most 12
  characters, unique labels within a question). It also builds the problem a malformed call is
  refused with, the step's words, and the text the model reads back.

It stays apart from `McpField`. The two come from different protocols with different shapes, and
forcing one model on both would make each worse. What they share is the row the user taps (D6).

**D4 — The answer is a result, never a failure.** The model reads, as the tool's result:
- **Answered:** each question with its answer, or "in their own words: …".
- **Said:** the user's words, as a reply instead of choosing.
- **Skipped:** that the user closed the questions without answering, and that it should go on with
  its own judgement and say what it assumed, or stop.

The step shows Done in every case. A malformed call is an `AssistantToolException` naming the rule
it broke, so the model corrects itself in its next step. Stop stays cancellation, as for every call.

**D5 — The composer answers too.** While questions wait in the chat shown, a message sent from the
composer, from voice, or from the bubble's field is the `Said` reply. It does not start a turn
refused as "working". The rule lives once, in `AssistantCoordinator.send`. Send is enabled then, for
text without attachments. This is also the voice answer path that approvals lack.

**D6 — One card, the design system's rows.** The questions are a card above the composer, the same
shape as the approval card: the header, the question, its options, Other, then Skip and Submit
(Next, until the last question).
- Several questions show one at a time, their headers as steps with the answered ones ticked.
  Back and Next move between them, and Submit sends them all.
- `IcodeOptionRow` gains `multiple`: a tick in place of the radio, and the whole row toggles with
  the checkbox role. Its description becomes optional.
- The questions card and MCP's form both use `IcodeOptionRow`. This closes MCP's touch-target gap
  and removes its private checkbox row.
- The card's body scrolls beyond a capped height, so four questions with Other open never push the
  chat off screen.

**D7 — Every surface that shows a waiting question shows this one:**
- the step's status (Asking);
- the step's sheet, which shows the same card and answers as the chat's;
- the notification ("The assistant asks: …");
- the bubble: its first question, "Open in iCode", and the bubble's field, which answers by D5;
- the spoken turn: the question and its options, said.

The words come from `AssistantQuestionRules`, written once.

**D8 — Boundaries:**
- **Kind and permission.** The tool is kind Read with no reach, so it never asks for approval. It
  is offered in every mode and under every permission: asking what the user wants is not asking
  for permission. The Full-access instruction "Nothing is asked of the user" becomes "No approval
  is asked".
- **One at a time.** Calls run one at a time and turns run one at a time, so there is at most one
  set of questions. A second `ask_user` in the same reply waits its turn like any other call.
- **No time limit.** Stop ends the wait, as it ends an approval.
- **Not saved while pending.** As for approvals: after a process death the call is closed as
  stopped. Answered questions are kept for free: the call's arguments hold the questions and its
  result holds the answers, so the step's page shows both after the turn and after a restart.
- **Scope.** No previews, images or code in options, and no answer from the notification. The
  rendering can grow later without changing the tool.

**D9 — A retry goes on from the chat.** When a provider failure is retried after the model has
already replied in this turn, the request is rebuilt from the chat as saved, as the step-limit pause
does. What was asked and answered, and what was run, is not done again. The rule is one function,
`AssistantRules.continued`, used by both paths.

## 4. Cycles

- **Q1 — domain.** `AssistantClarification`, `AssistantQuestion`, `AssistantReply`,
  `AssistantQuestionRules`, `AssistantState.clarification`, `ICodeIntent.ReplyAssistant`,
  `AssistantToolName.AskUser`, the Asking status and `AssistantRules.continued`. Tests: limits and
  refusals, the result text for each reply, the status, and the continued request.
- **Q2 — backend.** The third slot and the shared wait in `AssistantQuestions`; the coordinator
  routes `ReplyAssistant` and a message sent while questions wait. The `ask_user` tool goes in
  `AssistantToolbox`, with the retry from the chat and the instructions. Tests: a call waits and
  returns the answer; a stale call id is ignored; a message in the chat is the reply; Stop ends the
  wait; a malformed call is refused.
- **Q3 — UI.** `IcodeOptionRow(multiple)`, the questions card, the step sheet's footer, the composer
  rule, and MCP's form on the same rows.
- **Q4 — every surface.** The notification, the bubble, and the spoken turn. Tests for each rule.
- **Q5 — docs as built.** This plan, the CHANGELOG, and the permissions and sheets plans where they
  list what a turn asks.

## 5. As built

- **Domain:** `domain/AssistantClarification.kt` (the model, `AssistantQuestionRules`);
  `AssistantState.clarification`, `asking` and `replying`; `ICodeIntent.ReplyAssistant`;
  `AssistantToolName.AskUser`; the Asking status in `AssistantRules.entries`; `AssistantRules.replied`.
- **Backend:**
  - `AssistantQuestions` holds three slots that share one `wait`. Its `reply` takes an answer only
    for the call asked, and `replyWith` takes a message sent in the chat that waits.
  - `AssistantCoordinator` makes the questions and routes `AnswerAssistant`, `ReplyAssistant` and
    `ContinueAssistant` to them. In `send`, a message without files in the waiting chat is the reply.
  - `AssistantTurns` takes the questions, wires `AssistantStep.clarify`, clears them at the end of
    the turn, and goes on from the chat on a retry (`goOn`, shared with the step-limit pause).
  - `tools/AskUserTool.kt` is listed last in `AssistantToolbox.forWorkspace`.
  - The instructions: one rule under "Tool use", Plan mode's line, and Full access's sentence.
- **UI:**
  - `IcodeOptionRow(multiple)` with an optional description, and the token `IcodeSizes.QuestionAnswers`.
  - `AssistantQuestionsCard` in the chat and in the step's sheet.
  - The composer: Send replies, a heard reply is sent, and the hint is "Or answer in your own words".
  - MCP's choices are on `IcodeOptionRow`.
  - The bubble: the question, "Open in iCode", "Your answer…", and Send while replying.
  - The notification and the spoken turn.
- **Tests:**
  - `AssistantQuestionRulesTest` (7): the limits and refusals, the result for each reply, Submit's
    rule, the words of every surface, Asking, replying and the bubble, the notification and the
    voice, and when a retry goes on.
  - `AskUserToolTest` (5): the wait and the answers read back, a stale call's reply ignored, a
    message in the waiting chat as the reply, Stop, a refused call, and a list sent as text.
- **Gate:** unit tests run with only `TreeSizeTest`'s root-only case failing, as on main;
  assembleDebug and assembleRelease build; both consistency guards pass; lint shows only main's
  error.

## 6. Phone checks (owner)

1. In Plan mode, ask for something with two clear approaches. The card shows the question, the
   options with their descriptions, and Other. The answer continues the same turn.
2. Four questions: the steps tick as each is answered, Submit sends them all, and the step's page
   shows each question with its answer.
3. Type a reply in the composer while the card shows: it answers, and nothing is refused.
4. From the bubble, aloud: the question and its options are spoken; a spoken reply answers.
5. Leave iCode while the card shows: "Needs your answer" says the question; the tap opens the chat.
6. TalkBack: a single choice is a radio group; a multiple choice's rows are checkboxes with their
   descriptions.
