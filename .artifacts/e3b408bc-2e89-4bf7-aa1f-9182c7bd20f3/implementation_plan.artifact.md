# Implementation Plan - Quick Edit Mode for InvestScreen

Add a dedicated "Edit Mode" to the "Investir" tab that provides a simplified, large-font interface for updating share counts and prices on mobile devices.

## User Review Required

> [!IMPORTANT]
> **Toggle Mechanism**: A new "Edit" icon will be added next to the "Simulador de Aportes" title. Clicking it will switch the entire screen content to the simplified editing view.
> **Large Inputs**: In this mode, only "Ticker", "Cotas", and "Preço" will be shown. The font size will be significantly increased (to ~18sp) and input heights will be adjusted for easier finger tapping.

## Proposed Changes

### [UI Components]

#### [MODIFY] [B3CheckUI.kt](file:///home/elias/AndroidStudioProjects/B3Check/shared/src/commonMain/kotlin/com/example/b3check/B3CheckUI.kt)

- **InvestScreen**:
    - Add `var isEditMode by rememberSaveable { mutableStateOf(false) }`.
    - Modify the header Row to include an `IconButton(Icons.Default.Edit)` or `TextButton("Editar")`.
    - Conditional Content:
        - **Normal Mode**: Keep the existing 5-column simulation grid.
        - **Edit Mode**: Display a new `LazyColumn` with rows containing only Ticker, Cotas (TextField), and Preço (TextField) with large styling.
    - Add a "Voltar" or "Salvar" button at the bottom of the Edit Mode to return to the simulation.

## Verification Plan

### Manual Verification
1.  **Open Investir Tab**: Verify the "Edit" button is visible.
2.  **Enter Edit Mode**: Click the button and verify the UI changes to a simpler 3-column list with large text.
3.  **Perform Edits**: Change a few share counts and prices. Verify that the keyboard doesn't cover the inputs (relying on the previously implemented `imePadding`).
4.  **Exit Edit Mode**: Click "Concluir" or the back icon.
5.  **Verify Results**: Ensure the simulation grid in Normal Mode immediately reflects the changes made in Edit Mode.
