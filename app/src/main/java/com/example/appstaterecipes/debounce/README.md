# Debounce Recipe (`DebounceEffect` and `rememberDebounced`)

This recipe demonstrates how to debounce rapidly changing state inside a `transform` block (or any `@Composable` function) using two composable helpers:
- **`DebounceEffect(value, timeoutMillis, onDebounced)`**: Runs a suspending side effect (such as an asynchronous network request or autosave) after `value` has remained unchanged for `timeoutMillis`.
- **`rememberDebounced(value, timeoutMillis)`**: Projects a debounced `State<T>` built on top of `DebounceEffect` for deriving downstream UI or business state.

---

## Comparing `rememberDebounced` / `DebounceEffect` vs. `Flow.debounce`

Both approaches delay downstream work until an input stops changing for a specified quiet window, but they operate under different execution models.

### Side-by-Side Code

```kotlin
// Coroutine Flow approach
// 1. State transformation (Snapshot -> Flow -> StateFlow):
val debouncedQueryFlow: StateFlow<String> = snapshotFlow { textFieldState.text.toString() }
    .debounce(300L)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

// 2. Or side effect on a separate Flow:
viewModelScope.launch {
    snapshotFlow { textFieldState.text.toString() }
        .debounce(300L)
        .collectLatest { query ->
            searchApi(query)
        }
}
```

```kotlin
// AppState / Compose State approach
val uiState: State<DebounceUiState> = transform(
    initialValue = DebounceUiState(),
    scope = viewModelScope,
) {
    val rawQuery = textFieldState.text.toString()

    // Option 1 — State transformation:
    val debouncedQuery by rememberDebounced(rawQuery, timeoutMillis = 300L)

    // Option 2 — Side effect:
    DebounceEffect(rawQuery, timeoutMillis = 300L) { query ->
        searchApi(query)
    }

    DebounceUiState(debouncedQuery = debouncedQuery)
}
```

---

### Direct Differences

#### 1. Direct State Reads vs. Stream Conversion Bridges
- **`Flow.debounce`**: Operates on a `Flow`. When your source of truth is Compose state (such as `TextFieldState.text` or a `StateStore` key), you must bridge out of the snapshot system with `snapshotFlow { ... }`, apply `.debounce()`, and bridge back into state via `.stateIn(...)` / `collectAsStateWithLifecycle()`.
- **`rememberDebounced`**: Takes a plain value `T` read directly inside a `@Composable` scope and returns a Compose `State<T>` with no stream conversion ceremony.

#### 2. Initial Value Timing
- **`rememberDebounced`**: Initializes `remember { mutableStateOf(value) }` synchronously with the initial `value` on first composition (0ms delay), then debounces subsequent updates by `timeoutMillis`.
- **`DebounceEffect`**: Launches its countdown on initial composition and invokes `onDebounced(value)` after `timeoutMillis` (matching `snapshotFlow { ... }.debounce(300L).collectLatest { ... }`).

#### 3. Cancellation and Timer Restarts
- **`Flow.debounce` + `collectLatest`**: `debounce` manages an internal coroutine timer before emitting downstream, and `collectLatest` cancels any in-flight `searchApi(query)` call when a new debounced value arrives.
- **`DebounceEffect`**: Uses Compose's `LaunchedEffect(value, timeoutMillis)` lifecycle. Whenever `value` changes—either during the `delay(timeoutMillis)` countdown or while the suspending `onDebounced(value)` callback is still executing—Compose automatically cancels the previous coroutine job and starts a fresh countdown.

#### 4. Deduplication on `A -> B -> A` Bounces
- **`rememberDebounced` (and `Flow.stateIn`)**: Backed by `mutableStateOf(value)` with `structuralEqualityPolicy()` (`equals()`). If `value` briefly changes from `"kotlin"` to `"kotlin "` and back to `"kotlin"` before `timeoutMillis` expires, `"kotlin "` never settles, and when `"kotlin"` finishes its timer, assigning `"kotlin"` to `state.value` is a no-op that does not trigger downstream recomposition.
- **`DebounceEffect` (and bare `Flow.debounce`)**: Does not track the previously settled value. If `value` bounces `A -> B -> A` within `timeoutMillis`, the timer restarts on each change and `onDebounced(A)` will be invoked again after `timeoutMillis`. (If you need deduplicated side effects, key a `LaunchedEffect(debouncedValue)` on the output of `rememberDebounced`.)

#### 5. Recomposition Safety (`rememberUpdatedState`)
- Because `DebounceEffect` is a `@Composable` function, unrelated state changes in the surrounding `transform` or UI block can cause recomposition while the debounce timer is still counting down.
- Wrapping `onDebounced` in `rememberUpdatedState` allows recompositions to supply an updated callback closure without adding `onDebounced` to the `LaunchedEffect` keys (which would reset the timer on every recomposition) and without capturing stale variables from an earlier composition.

#### 6. Dynamic Timeouts
- **`Flow.debounce`**: Uses either a fixed timeout or a per-item selector lambda (`debounce { value -> timeoutFor(value) }`) evaluated only when an item is emitted.
- **`DebounceEffect` / `rememberDebounced`**: `timeoutMillis` is a reactive `LaunchedEffect` key. Changing `timeoutMillis` immediately restarts the active timer with the new duration (note that in `DebounceEffect`, changing `timeoutMillis` while `value` is idle will also re-run the effect once with the new timeout).

---

## How It Works

1. **`DebounceEffect`**:
   - Keys a `LaunchedEffect(value, timeoutMillis)` on the input `value` and timeout duration.
   - Whenever `value` changes before `delay(timeoutMillis)` completes, Compose cancels the in-flight coroutine and restarts the countdown.
   - Uses `rememberUpdatedState(onDebounced)` so recompositions passing a new lambda instance neither restart the timer nor capture stale state.
2. **`rememberDebounced`**:
   - Holds an internal `remember { mutableStateOf(value) }` initialized to the starting `value` and forwards settled updates from `DebounceEffect`.
   - Because `mutableStateOf` uses structural equality (`equals()`), updates that settle back to the existing value do not trigger redundant downstream recompositions.
