# Implementation Plan - Simplify InvestScreen Normal Mode

Remove direct editing from the simulation grid in the "Investir" tab, making it a read-only results view and centralizing all modifications in the new "Quick Edit" mode.

## User Review Required

> [!IMPORTANT]
> **Read-Only Grid**: The "Cotas" and "Preço" columns in the main simulation view will no longer be editable text fields. They will display as standard text.
> **Centralized Editing**: All updates to quantities and prices will now happen exclusively through the **Edit (Pencil)** button. This prevents accidental keyboard popups and clarifies the app's workflow.

## Proposed Changes

### [UI Components]

#### [MODIFY] [B3CheckUI.kt](file:///home/elias/AndroidStudioProjects/B3Check/shared/src/commonMain/kotlin/com/example/b3check/B3CheckUI.kt)

- **InvestScreen (Normal Mode Branch)**:
    - Locate the `BasicTextField` for `sharesCount`. Replace it with a `Text` component using the same weight (`1.1f`) and alignment.
    - Locate the `BasicTextField` for `currentPrice`. Replace it with a `Text` component using the same weight (`0.9f`) and alignment.
    - Keep the `editStates` map for the "Quick Edit" mode's internal use, but it will no longer be needed for the normal grid.

## Verification Plan

### Manual Verification
1.  **Open Investir Tab**: Verify the "Cotas" and "Preço" columns show the current data as plain text.
2.  **Scroll Test**: Swipe through the list. Verify that the keyboard never appears and the UI feels stable.
3.  **Edit Workflow**:
    - Tap the pencil icon.
    - Change a value in Quick Edit Mode.
    - Tap "Concluir".
    - Verify the updated value is correctly displayed as text in the normal grid.
