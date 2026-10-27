# GitHub Browser (full app sample)

Unlike the single-concept recipes in [`app`](../app), this module is a complete app: a
Jetpack Compose port of the classic
[GithubBrowserSample](https://github.com/android/architecture-components-samples/tree/main/GithubBrowserSample)
that searches GitHub repositories, shows repository details and contributors, shows user profiles,
and lets you sign in with your GitHub account.

It shows how the AppState libraries fit into a real, multi-screen app:
- **`StateStore`** holds the **authentication state** (access token, signed-in login, signed-in user,
  and login UI state) in one place, shared by the network layer, repositories, and every screen.
  With `statestore-datastore`, the token and login persist across app restarts.
- **`Transform`** handles **state management throughout the app**. Each `ViewModel` exposes its UI
  state as Compose `State` produced by `transform { ... }` instead of `StateFlow` pipelines.

---

## StateStore for authentication

### Keys and accessors
[`AuthState.kt`](src/main/java/com/android/example/github/repository/AuthState.kt) defines a
type-safe `StateStoreKey` for each piece of authentication state, plus `StateStore` extension
functions to read and write them:

```kotlin
@Serializable
object AccessTokenKey : StateStoreKey<String>(""), PersistToDataStore

@Serializable
object LoggedInUserLoginKey : StateStoreKey<String>(""), PersistToDataStore

object LoggedInUserKey : StateStoreKey<User?>(
    defaultValue = null,
    autoClearKey = LoggedInUserLoginKey,
    shouldClearState = { stateStore -> stateStore.loggedInUserLogin().value.isBlank() }
)

object LoginUiStateKey : StateStoreKey<LoginUiState>(LoginUiState.Idle)
```

- `AccessTokenKey` and `LoggedInUserLoginKey` implement `PersistToDataStore`, so they are saved to
  and restored from DataStore. A signed-out state is stored as `""` rather than `null` so that
  logging out is persisted too.
- `LoggedInUserKey` uses `autoClearKey` so the cached `User` is cleared automatically when the
  signed-in login is cleared.
- `LoginUiStateKey` holds the progress of the device-flow login dialog (not persisted).

### One shared `StateStore`
[`AppModule`](src/main/java/com/android/example/github/di/AppModule.kt) provides a single
`@Singleton` `StateStore`, and
[`GithubApp`](src/main/java/com/android/example/github/GithubApp.kt) syncs it to a DataStore file
when the app starts:

```kotlin
applicationScope.launch {
    stateStore.syncToDataStore(filesDir.resolve("github_auth_statestore.json").absolutePath, this)
}
```

Persisted keys are read once in `provideStateStore()` so they are registered before
`syncToDataStore` runs, and their values are restored at startup.

### Every component reads the same state
Because the authentication state lives in one `StateStore` instead of being copied into each
class, changes show up everywhere without extra plumbing:

| Component | How it uses the `StateStore` |
|---|---|
| [`AuthRepository`](src/main/java/com/android/example/github/repository/AuthRepository.kt) | Writes the token and user after the [GitHub device flow](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow) succeeds, and clears them on logout or failure. Exposes `isLoggedIn` and `loggedInUserLogin`. |
| [`AuthInterceptor`](src/main/java/com/android/example/github/api/AuthInterceptor.kt) | Reads `stateStore.accessToken().value` on every request to add the `Authorization` header. |
| [`SearchViewModel`](src/main/java/com/android/example/github/ui/search/SearchViewModel.kt) | Drives the login dialog through `LoginUiStateKey` and shows the signed-in user. |
| [`UserViewModel`](src/main/java/com/android/example/github/ui/user/UserViewModel.kt) | Shows a **Logout** action when you view your own profile. Logging out here also updates the search screen. |
| [`RepoViewModel`](src/main/java/com/android/example/github/ui/repo/RepoViewModel.kt) | Reloads repository data when the signed-in user changes, so requests use the new credentials. |

---

## Transform for state management

Each `ViewModel` builds its UI state with `transform`, a headless composition running in
`viewModelScope`. Inside the block you read Compose state (including `StateStore` state) directly,
and the block re-runs whenever any of it changes:

```kotlin
val results: State<Resource<List<Repo>>?> = transform(
    initialValue = null,
    scope = viewModelScope,
    dispatcher = dispatcher
) {
    val currentQuery = _query.value
    val trigger = refreshTrigger.intValue
    val activeLogin = stateStore.loggedInUserLogin().value
    if (currentQuery.isEmpty()) {
        null
    } else {
        remember(currentQuery, trigger, activeLogin) { repoRepository.search(currentQuery) }
            .collectAsState(initial = null).value
    }
}
```

A few patterns to note:
- **Mix local and shared state.** The block reads the screen's own state (`_query`,
  `refreshTrigger`) and the shared authentication state (`loggedInUserLogin`) the same way, with no
  `combine` or `flatMapLatest`.
- **Wrap `Flow` creation in `remember`.** Repositories still return `Flow`s from Room and Retrofit.
  Creating the `Flow` inside `remember(keys)` and collecting it with `collectAsState` restarts the
  request only when a key changes. Without `remember`, each emission would create a new `Flow`
  and restart collection.
- **Reload with keys.** Adding `trigger` or `activeLogin` to the `remember` keys is how retry,
  refresh, and "reload after sign-in" are implemented.
- **Write state in effects, not in composition.** Screens pass navigation arguments to their
  `ViewModel` inside `LaunchedEffect` (for example `LaunchedEffect(login) { setLogin(login) }`),
  and the `transform` blocks only read state.

`UserViewModel` and `RepoViewModel` share this pattern through small `loadForLogin` /
`loadForRepo` helpers.

---

## Running the sample

Select the **githubbrowser** run configuration in Android Studio, or run:

```
./gradlew :githubbrowser:installDebug
```

Search works without signing in. To sign in, tap **Login with GitHub**, copy the code shown in the
dialog, and enter it at <https://github.com/login/device>.

The sample includes a default GitHub OAuth App client ID with device flow enabled. To use your own,
[create an OAuth App](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app),
enable **Device Flow**, and add its client ID to `local.properties`:

```
github.client.id=<your client id>
```

## Tests

```
./gradlew :githubbrowser:testDebugUnitTest
```

The unit tests run on the JVM and pass a test dispatcher into each `ViewModel`. They cover:
- [`AuthRepositoryTest`](src/test/java/com/android/example/github/repository/AuthRepositoryTest.kt):
  login, logout, and `StateStore` updates.
- [`SearchViewModelTest`](src/test/java/com/android/example/github/ui/search/SearchViewModelTest.kt):
  authentication staying in sync across `SearchViewModel`, `UserViewModel`, and `RepoViewModel`
  through the shared `StateStore`.

## Libraries used
- AppState: `statestore`, `statestore-datastore`, `transform`
- Hilt with [`androidx.hilt:hilt-lifecycle-viewmodel-compose`](https://developer.android.com/jetpack/androidx/releases/hilt) (`hiltViewModel()`)
- Navigation 3, Room, Retrofit, Coil
