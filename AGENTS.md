## Commits

Commit completed changes as each separate task is finished. Keep independently reviewable tasks in separate commits rather than combining them, and include the required sign-off on every commit.

## Visual changes

For tickets that change visible app or system-interface behavior, use an Android emulator when one is available. Build and install the app, reproduce the relevant state with the real implementation, capture a screenshot with Android Debug Bridge (ADB), inspect the image, and display it to the user as part of the ticket report. Keep ticket-specific screenshots and temporary preview helpers out of the commit unless the ticket asks for them.
