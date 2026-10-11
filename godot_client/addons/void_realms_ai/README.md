# VOID REALMS AI — DeepSeek Godot 4.7.2 Development Plugin

"VOID REALMS AI" is an in-editor AI development assistant plugin built for Godot 4 (compatible with Godot 4.7.2). It connects directly to official DeepSeek API endpoints to analyze GDScript source files, inspect project context, suggest code refactoring, and safely apply file changes with explicit user confirmation and automated backups.

---

## 🌟 Features

1. **In-Editor Chat Dock ("VOID REALMS AI")**:
   - Integrated directly into Godot Editor's bottom panel.
   - RichText BBCode response formatting with code block highlighting.
   - Optional project context scanning (`res://scripts`, `res://scenes`).

2. **Official DeepSeek API Integration**:
   - Real HTTP connection using Godot `HTTPRequest`.
   - Supports official DeepSeek endpoints (e.g. `https://api.deepseek.com/chat/completions`).
   - Configurable API endpoint, model name (`deepseek-chat`), and API key.

3. **Secure Local Credentials Management**:
   - API Key is stored exclusively in local user data (`user://void_realms_ai_config.json`).
   - **Zero credentials** written to source code, project settings, `.tscn` files, or version control repositories.

4. **Safe File Modification & User Approval**:
   - Proposals extracted from AI responses are displayed in the "Değişiklik Önerileri" (Proposed Changes) tab.
   - **No code is modified automatically.** Each change requires explicit user click on "Değişiklikleri Uygula (Approve)".
   - Code execution safeguards: No arbitrary shell commands, no untrusted script execution.

5. **Automated Backup & Rollback System**:
   - Before any file modification is written, a timestamped snapshot is saved to `user://void_realms_ai_backups/`.
   - Instant rollback capability via the "Yedekler & Geri Alma" tab.

6. **Comprehensive Error Handling**:
   - User-friendly messages for Network Disconnections, Invalid API Key (`401 Unauthorized`), Quota / Rate Limit exceeded (`402` / `429`), and Server Errors (`500+`).

7. **Android Godot 4.7.2 Compatibility Statement**:
   - Standard Godot `EditorPlugin` and `EditorInterface` APIs are built specifically for Desktop Godot Editor.
   - On Android runtime (exported APK or Android editor limitations), editor docks and editor filesystem controls are unsupported or limited. The plugin explicitly detects Android runtime (`OS.get_name() == "Android"`) and displays a clear status notice without pretending to support missing mobile editor features.

---

## 🚀 Installation & Enablement

1. Place the plugin folder inside your project:
   `godot_client/addons/void_realms_ai/`
2. Open Godot Editor:
   Go to `Project` -> `Project Settings` -> `Plugins`.
3. Locate **VOID REALMS AI** and check **Enable**.
4. A bottom panel named **"VOID REALMS AI"** will appear in your editor layout.

---

## 🔑 DeepSeek API Setup

1. Open the **"Ayarlar" (Settings)** tab in the VOID REALMS AI panel.
2. Enter your **DeepSeek API Key** (`sk-...`).
3. Set your preferred **Endpoint** (default: `https://api.deepseek.com/chat/completions`).
4. Set your target **Model** (default: `deepseek-chat`).
5. Click **"Ayarları Kaydet"**.

---

## 🔐 Security & Non-Interference Guarantee

- Existing core files (`Player3DController.gd`, `Main3D.tscn`, Android Kotlin client, Node.js server) remain 100% untouched and unaffected.
- The plugin strictly restricts file write access to `res://` project paths upon explicit user approval.
