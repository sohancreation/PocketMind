Bundled Model Assets

If you place a GGUF model file in this folder before building, the app can
auto-install it into the app's models directory on first run (no download).

Bundled SmolLM2 Mini (offline default):
- Put this file in `_models/` at the repo root:
  SmolLM2-360M-Instruct-Q4_K_M.gguf
- The build automatically bundles it into the APK assets under `models/`.

Bundled TinyLlama (optional, very large):
- Put this file in `_models/` at the repo root:
  tinyllama-1.1b-chat.Q4_K_M.gguf
- If you want to bundle it too, run the `copyBundledTinyLlama` Gradle task before building.

Build:
- gradlew.bat clean assembleDebug

Notes:
- Bundling large models will make the APK very large (hundreds of MB).
- Sharing such a large APK via WhatsApp may fail depending on file limits.
