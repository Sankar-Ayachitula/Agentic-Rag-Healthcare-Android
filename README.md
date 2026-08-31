# Agentic-Rag-Healthcare-Android

Native Android client (Kotlin + Jetpack Compose) for the
[Agentic-Rag-Healthcare](https://github.com/Sankar-Ayachitula/Agentic-Rag-Healthcare)
backend. A chat UI that talks to the backend's `POST /chat` endpoint.

## Stack
- Kotlin + Jetpack Compose (Material 3)
- MVVM (ViewModel + StateFlow)
- Retrofit + Moshi + OkHttp, Kotlin coroutines
- Word-by-word typewriter reveal for assistant replies

## Run it
1. Start the backend on your machine:
   `uvicorn backend.main:app --reload`   (from the backend repo)
2. Open **this** project in Android Studio and let Gradle sync.
3. Run on an **emulator**. The app targets `http://10.0.2.2:8000` (the emulator's
   alias for your machine's localhost).

### Physical device or deployed backend
- Physical device: change `BASE_URL` in `data/ChatApi.kt` to your machine's LAN IP
  (e.g. `http://192.168.1.20:8000/`) and run the backend with `--host 0.0.0.0`.
- Production: deploy the backend to an HTTPS URL, set `BASE_URL` to it, and remove
  the cleartext entries in `res/xml/network_security_config.xml`.

## API contract
```
POST /chat
  request:  { "message": "string" }
  response: { "answer": str, "intent": str, "disease": str|null,
              "symptoms": [str]|null, "sources": [ { "page": int, ... } ]|null }
```

## Structure
```
app/src/main/java/ai/studio1119/medassist/
  MainActivity.kt          entry point
  data/ChatModels.kt       request/response data classes
  data/ChatApi.kt          Retrofit service + client (BASE_URL here)
  ui/ChatViewModel.kt      state + send logic
  ui/ChatScreen.kt         chat UI (bubbles, input bar, typewriter, sources)
  ui/theme/                colors + Material3 theme
```

> Note: the Gradle wrapper JAR is not committed. On first open, let Android Studio
> generate the wrapper (or run `gradle wrapper`).
