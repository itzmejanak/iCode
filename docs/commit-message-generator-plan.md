## AI Commit Message Generator

 Add a **magic ✨ button inside the commit message input field** on the Source Control screen ensuring that no duplicay no inconsistency if needed any third-party native integration for better functionality without comparmising the scalability and follow the best practices of abstraction and without overengenering make exactly what and how it need to be.

 When the user has staged changes, they can tap the magic button and the AI will:

 1. Look at the currently staged code changes.
2. Understand what was changed and why.
3. Generate a concise, meaningful commit message.
4. Automatically place that message into the commit message field.
5. Let the user review or edit it before committing.

### User Experience

 - **No staged changes:** Magic button is disabled.
- **Staged changes available:** Magic button is active.
- **Generating:** Button shows a small loading animation.
- **Generation complete:** The generated commit message appears directly in the input field.
- **Generation fails:** Show a clear, friendly error message and keep the existing text unchanged.
- **AI provider/model:** Use whichever AI assistant the user has currently configured and Active by default but from source control settings user can assign any provider model for default overide.

 ### Example

 The user stages changes such as:

 - Added a login screen.
- Added password validation.
- Updated authentication handling.

 They tap **✨ Generate**.

 The input automatically becomes:

 > `feat(auth): add login validation and authentication flow`

 The user can then edit it if needed and commit normally.

 ### Overall Goal

 Make creating commit messages **one tap instead of a manual task** while keeping the experience fast, unobtrusive, and consistent with the existing Source Control UI.