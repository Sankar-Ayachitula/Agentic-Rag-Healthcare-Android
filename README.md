# Agentic-Rag-Healthcare-Android

Native Android client (Kotlin + Jetpack Compose) for the
[Agentic-Rag-Healthcare](https://github.com/Sankar-Ayachitula/Agentic-Rag-Healthcare)
backend. A chat UI that streams answers from the backend's `POST /chat/stream` endpoint.

## Stack
- Kotlin + Jetpack Compose (Material 3)
- MVVM (ViewModel + StateFlow)
- OkHttp + Server-Sent Events (SSE), Kotlin coroutines / Flow
- Real token-by-token streaming: assistant replies grow live as the backend streams them

## Run it
1. Start the backend on your machine:
   `uvicorn backend.main:app --reload`   (from the backend repo)
2. Open **this** project in Android Studio and let Gradle sync.
3. Run on an **emulator**. The app streams from `http://10.0.2.2:8000/chat/stream`
   (`10.0.2.2` is the emulator's alias for your machine's localhost).

### Physical device or deployed backend
- Physical device: change `BASE_URL` in `data/StreamingClient.kt` to your machine's LAN IP
  (e.g. `http://192.168.1.20:8000/`) and run the backend with `--host 0.0.0.0`.
- Production: deploy the backend to an HTTPS URL, set `BASE_URL` to it, and remove
  the cleartext entries in `res/xml/network_security_config.xml`.
  (For a LAN IP over plain HTTP, add that IP as a `<domain>` there too.)

## API contract
This app uses the streaming endpoint:
```
POST /chat/stream   (Server-Sent Events)
  request: { "message": "string" }
  stream:  data: {"type":"meta","intent":...,"disease":str|null,"symptoms":[...],"sources":[pageInt,...]}
           data: {"type":"token","text":"..."}     (many, in order)
           data: {"type":"done"}
           data: {"type":"error","message":"..."}   (instead of done, if the backend fails)
```
An empty message is rejected with `422`.
The backend also exposes a non-streaming `POST /chat` that returns the full answer at once.

## Structure
```
app/src/main/java/ai/studio1119/medassist/
  MainActivity.kt          entry point
  data/StreamingClient.kt  SSE client (BASE_URL here) + StreamEvent types
  ui/ChatViewModel.kt      state + streaming send logic (grows the message live)
  ui/ChatScreen.kt         chat UI (bubbles, input bar, disease chip, sources)
  ui/theme/                colors + Material3 theme
```

Build from the command line with `./gradlew assembleDebug` (needs JDK 17).
