@tool
extends Node
class_name DeepSeekAPIClient

signal request_started()
signal request_completed(success: bool, content: String, error_message: String)

var http_request: HTTPRequest

const SYSTEM_PROMPT: String = """You are VOID REALMS AI, an expert Godot 4 (GDScript) development assistant embedded in Godot Editor.
Your task is to analyze GDScript code, scene structures, and game architecture to provide precise bug fixes, refactoring, and code features.

Rules:
1. Always output modern Godot 4 GDScript (e.g. use @export, @onready, CharacterBody3D, move_and_slide(), signal definitions).
2. When proposing code or file modifications, format your code block clearly and specify target file paths when relevant.
3. Keep answers concise, direct, and actionable. Do not attempt to execute shell commands or arbitrary local operations."""

func _ready() -> void:
	_ensure_http_request()

func _ensure_http_request() -> void:
	if not http_request:
		http_request = HTTPRequest.new()
		add_child(http_request)
		http_request.request_completed.connect(_on_http_request_completed)

func send_chat_prompt(user_prompt: String, system_context: String = "") -> void:
	_ensure_http_request()

	var config := VoidRealmsAIConfigManager.load_config()
	var endpoint: String = config.get("endpoint", VoidRealmsAIConfigManager.DEFAULT_ENDPOINT)
	var model: String = config.get("model", VoidRealmsAIConfigManager.DEFAULT_MODEL)
	var api_key: String = config.get("api_key", "")

	if api_key.is_empty():
		request_completed.emit(false, "", "Hata: DeepSeek API anahtarı ayarlanmamış. Lütfen 'Ayarlar' sekmesinden geçerli bir API anahtarı girin.")
		return

	var final_system_prompt := SYSTEM_PROMPT
	if not system_context.is_empty():
		final_system_prompt += "\n\nProject Context:\n" + system_context

	var messages := [
		{"role": "system", "content": final_system_prompt},
		{"role": "user", "content": user_prompt}
	]

	var payload := {
		"model": model,
		"messages": messages,
		"temperature": 0.2,
		"stream": false
	}

	var json_payload := JSON.stringify(payload)
	var headers := PackedStringArray([
		"Content-Type: application/json",
		"Authorization: Bearer " + api_key
	])

	request_started.emit()
	var err := http_request.request(endpoint, headers, HTTPClient.METHOD_POST, json_payload)
	if err != OK:
		request_completed.emit(false, "", "Ağ Hatası: HTTP isteği başlatılamadı (Hata Kodu: " + str(err) + ").")

func _on_http_request_completed(result: int, response_code: int, _headers: PackedStringArray, body: PackedByteArray) -> void:
	if result != HTTPRequest.RESULT_SUCCESS:
		var err_msg := "Ağ Hatası: DeepSeek sunucusuna bağlanılamadı veya istek zaman aşımına uğradı (Result: " + str(result) + ")."
		request_completed.emit(false, "", err_msg)
		return

	var body_string := body.get_string_from_utf8()
	var json := JSON.new()
	var parse_err := json.parse(body_string)

	if parse_err != OK:
		var err_msg := "Yanıt Biçim Hatası: Sunucu geçersiz veya boş bir yanıt döndürdü (HTTP " + str(response_code) + ")."
		request_completed.emit(false, "", err_msg)
		return

	var response_dict: Dictionary = json.data if json.data is Dictionary else {}

	if response_code == 200:
		if response_dict.has("choices") and response_dict["choices"] is Array and not response_dict["choices"].is_empty():
			var first_choice: Dictionary = response_dict["choices"][0]
			if first_choice.has("message") and first_choice["message"] is Dictionary:
				var message_content: String = first_choice["message"].get("content", "")
				request_completed.emit(true, message_content, "")
				return
		request_completed.emit(false, "", "Hata: Sunucu yanıtında 'choices' içeriği bulunamadı.")
		return

	# Error status codes explicit formatting
	var error_detail := ""
	if response_dict.has("error") and response_dict["error"] is Dictionary:
		error_detail = response_dict["error"].get("message", "")

	var user_friendly_error := ""
	match response_code:
		401:
			user_friendly_error = "Geçersiz API Anahtarı (401 Unauthorized). Lütfen Ayarlar sekmesinden API anahtarınızı kontrol edin."
		402, 429:
			user_friendly_error = "Kota/Limit Hatası (HTTP " + str(response_code) + "): Bakiye yetersiz veya çok fazla istek gönderildi."
		500, 502, 503, 504:
			user_friendly_error = "DeepSeek Sunucu Hatası (HTTP " + str(response_code) + "). Lütfen bir süre sonra tekrar deneyin."
		_:
			user_friendly_error = "API Hatası (HTTP " + str(response_code) + ")."

	if not error_detail.is_empty():
		user_friendly_error += "\nDetay: " + error_detail

	request_completed.emit(false, "", user_friendly_error)
