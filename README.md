# Jetpack AppState - Code recipes

Jetpack AppState (`StateStore` and `Transform`) is a suite of libraries for managing, sharing, and transforming application state across Android components and UI surfaces. This repository contains recipes demonstrating how to use its APIs to implement common state management use cases. Each recipe introduces a single concept.

Recipes on the `main` branch use a pinned snapshot build, bumped regularly, of AppState.

## Recipes
These are the recipes and what they demonstrate.

### StateStore
*Recipes coming soon.*

### Transform
#### Rate limiting
- **[Debounce](app/src/main/java/com/example/appstaterecipes/debounce)**: Demonstrates how to debounce rapidly changing state (such as search queries) using `DebounceEffect` and `rememberDebounced`.
- **[Sample](app/src/main/java/com/example/appstaterecipes/sample)**: Demonstrates how to periodically sample high-frequency state streams (such as accelerometer sensor data) using `SampleEffect` and `rememberSampled`.

### Future recipes
The most upvoted [recipe requests](https://github.com/android/appstate-recipes/issues?q=is%3Aissue%20state%3Aopen%20label%3Arecipe-request) will be considered for implementation. Don't see your recipe? [File a request for one here](https://github.com/android/appstate-recipes/issues/new?template=1-recipe-request.md).

## Sample apps
Full apps that combine several AppState APIs in a realistic, multi-screen setting.

- **[GitHub Browser](githubbrowser)**: A Jetpack Compose GitHub client (search repositories, view repository details, contributors, and user profiles, and sign in with GitHub). It shows how to use `StateStore` to handle authentication: the access token and signed-in user live in one shared `StateStore`, persisted with `statestore-datastore`, so the network layer, repositories, and every screen stay in sync when you sign in or out. It also shows how to use `Transform` for state management throughout the app: each `ViewModel` builds its UI state with `transform` instead of `StateFlow` pipelines.

## Instructions
Clone this repository and open the root folder in [Android Studio](https://developer.android.com/studio). Each recipe is contained in its own package with its own `Activity` and `README.md`. Each sample app is its own Gradle module (for example `:githubbrowser`) with its own run configuration and `README.md`.

## Found an issue?
If the issue is _directly related to this project_, as in, it's reproducible without modifying this project's source code, then please [file an issue on GitHub](https://github.com/android/appstate-recipes/issues/new?template=2-bug-report.md). If you've found an issue with the Jetpack AppState libraries (`StateStore` or `Transform`), please [file an issue on the issue tracker](https://issuetracker.google.com/issues/new?component=2224885&template=2374332).

## Contributing
We'd love to accept your contributions. Please follow [these instructions](CONTRIBUTING.md).

## License
```
Copyright 2026 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
