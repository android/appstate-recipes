# Sample Recipe (`SampleEffect` and `rememberSampled`)

This recipe demonstrates how to periodically sample a high-frequency state stream inside a `transform` block (or any `@Composable` function) using two composable helpers:
- **`SampleEffect(value, periodMillis, onSampled)`**: Periodically invokes `onSampled` with the latest `value` every `periodMillis` without resetting the timer when `value` changes.
- **`rememberSampled(value, periodMillis)`**: Projects a sampled `State<T>` built on top of `SampleEffect` for throttling high-frequency inputs such as sensor streams, scrubbing gestures, or animations.

---

## Comparing `rememberSampled` / `SampleEffect` vs. `Flow.sample`

Both mechanisms throttle high-frequency data by periodically capturing the latest value at a fixed cadence, but their internal mechanics and usage differ.

### Side-by-Side Code

```kotlin
// Coroutine Flow approach
// 1. State transformation (Flow -> sampled StateFlow):
val sampledSensorFlow: StateFlow<Acceleration> = sensorFlow
    .sample(500L)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Acceleration())

// 2. Or side effect on a sampled Flow:
viewModelScope.launch {
    sensorFlow
        .sample(500L)
        .collect { acceleration ->
            logTelemetry(acceleration)
        }
}
```

```kotlin
// AppState / Compose State approach
val uiState: State<SampleUiState> = transform(
    initialValue = ImmutableSampleUiState(),
    scope = viewModelScope,
) {
    // Option 1 — State transformation (sampled streams with distinct cadences):
    val sampled250ms by rememberSampled(rawAcceleration, periodMillis = 250L)
    val sampled500ms by rememberSampled(rawAcceleration, periodMillis = 500L)
    val sampled1000ms by rememberSampled(rawAcceleration, periodMillis = 1000L)

    // Option 2 — Side effect:
    SampleEffect(rawAcceleration, periodMillis = 500L) { acceleration ->
        logTelemetry(acceleration)
    }

    // High-performance state projection:
    // Retain a single stable holder across executions to provide fine-grained property reads
    val state = remember { DelegatedSampleUiState() }
    state.raw = rawAcceleration
    state.rawHz = rawHz
    state.sampled250ms = sampled250ms
    state.sampled500ms = sampled500ms
    state.sampled1000ms = sampled1000ms
    state.isHardwareSensorAvailable = realTracker.isAvailable
    state.activeSource = activeSource

    state
}
```

---

### Direct Differences

#### 1. Direct State Reads vs. Channel Buffering
- **`Flow.sample`**: Spawns a coroutine producer with a conflated channel buffer and uses a `select` expression to race upstream emissions against a periodic ticker channel.
- **`rememberSampled`**: Operates directly on Compose snapshot state with no channel buffer. When the sampling interval elapses, it reads the current value from a snapshot-backed reference (`rememberUpdatedState`) and writes it to the returned `State<T>`.

#### 2. Timer Continuity Under Fast Updates
- **`Flow.sample`**: The sampling period runs on an independent ticker channel, so high-frequency upstream emissions do not interrupt or reset the period clock.
- **`SampleEffect`**: Achieves the same timer continuity in Compose by keying its ticker loop **only on `periodMillis`** (`LaunchedEffect(periodMillis)`). Rapid changes to `value` update `rememberUpdatedState(value)` and mark `hasPending = true` without cancelling or restarting the periodic timer loop.

#### 3. Handling Idle / Stationary Periods
- **`Flow.sample`**: Does not emit during intervals where no upstream values arrived since the previous tick, avoiding duplicate emissions when the source is idle.
- **`SampleEffect`**: Matches this behavior using a `hasPending` flag (note that on initial composition, the starting `value` marks `hasPending = true` for the first tick). On each subsequent tick, if no new `value` was composed during the interval (`hasPending == false`), `onSampled` is skipped.

#### 4. Deduplication When Values Fluctuate Back (`A -> B -> A`)
- **`SampleEffect` (and bare `Flow.sample`)**: If a sensor fluctuates from `50` to `60` and back to `50` within a single `500ms` window, a new value *did* arrive during the period (`hasPending == true`), so `onSampled(50)` will be invoked on the tick even though the previous tick also saw `50` (unless `Flow.sample` is chained with `.distinctUntilChanged()` or `.stateIn(...)`).
- **`rememberSampled` (and `Flow.sample` + `stateIn`)**: Writes into `remember { mutableStateOf(value) }`. Because `mutableStateOf` checks structural equality (`equals()`), if the sampled value at the tick boundary equals the prior sampled state, the write is a no-op and downstream recompositions are skipped automatically.

#### 5. Dynamic Period Adjustments
- **`Flow.sample`**: In standard `kotlinx.coroutines`, `sample(periodMillis)` takes a fixed duration for the lifetime of that operator stage; changing the period dynamically requires `flatMapLatest`.
- **`SampleEffect` / `rememberSampled`**: `periodMillis` is a reactive `LaunchedEffect` key. If the sampling rate changes dynamically (for example, switching between `100ms` active tracking and `1000ms` battery-saver tracking), the ticker loop automatically restarts with the new period.

---

## Fine-Grained `@Stable` UI State with Property Delegation

A common anti-pattern when working with high-frequency streams in Compose is returning a brand-new immutable `UiState` data class on every sensor event (60+ times/sec). Even if parts of the UI only care about a slower 1000ms sample, emitting a new top-level state object triggers recomposition of any composable reading `uiState`.

Instead, this recipe demonstrates returning a single **`@Stable` state interface** from a **single `transform` block**:

```kotlin
@Stable
interface SampleUiState {
    val raw: Acceleration
    val rawHz: Int
    val sampled250ms: Acceleration
    val sampled500ms: Acceleration
    val sampled1000ms: Acceleration
    val isHardwareSensorAvailable: Boolean
    val activeSource: SensorSource
}
```

### Key Performance Benefits:

1. **Single Headless Composition**:
   All sampled rates (`250ms`, `500ms`, `1000ms`) live in the same `transform` composition rather than managing multiple parallel `transform` scopes.
2. **Fine-Grained Snapshot Tracking**:
   The returned `SampleUiState` instance is created once via `remember { DelegatedSampleUiState() }`. Each property on the class is backed by an independent `mutableStateOf`. When `raw` fluctuates at 60Hz, only observers that read `uiState.raw` are invalidated; observers of `sampled1000ms` or static screen controls remain unaffected.
3. **Automatic Draw-Phase Optimization**:
   Because properties hook into individual Compose snapshot reads, passing `uiState` into a `Canvas` and reading `uiState.raw` inside the `Canvas` lambda allows Compose to completely skip Composition and Layout phases, re-executing only the Draw pass. (Note: Composables that observe `uiState.raw` during composition or layout, such as text labels, still recompose when the value changes.)

---

## How It Works

1. **`SampleEffect`**:
   - Keys its ticker `LaunchedEffect(periodMillis)` **only on `periodMillis`**, running a long-lived `while (isActive)` loop that delays for `periodMillis` on every tick.
   - Tracks whether `value` has updated since the last tick using a `SideEffect` and `AtomicBoolean`, eliminating coroutine cancellation and allocation churn at high frequencies without backwards writes during composition.
   - On each tick, it atomically checks and resets the pending flag (`compareAndSet(true, false)`) before invoking `currentOnSampled(latestValue)`. If no new value arrived during that period, it skips the callback.
   - Wraps `value` and `onSampled` with `rememberUpdatedState` so the ticker loop always reads the freshest value and callback without restarting the timer when either changes.
2. **`rememberSampled`**:
   - Holds an internal `remember { mutableStateOf(value) }` initialized to the starting `value` and updates it from `SampleEffect`.
   - Structural equality in `mutableStateOf` (`equals()`) prevents redundant downstream recompositions when sampled values across ticks are equal.
