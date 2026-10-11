@tool
extends RefCounted
class_name VoidRealmsAIConfigManager

# Configuration file location in user data directory (outside tracked source code)
const CONFIG_PATH: String = "user://void_realms_ai_config.json"

const DEFAULT_ENDPOINT: String = "https://api.deepseek.com/chat/completions"
const DEFAULT_MODEL: String = "deepseek-chat"

static func load_config() -> Dictionary:
	var config := {
		"endpoint": DEFAULT_ENDPOINT,
		"model": DEFAULT_MODEL,
		"api_key": ""
	}

	if not FileAccess.file_exists(CONFIG_PATH):
		return config

	var file := FileAccess.open(CONFIG_PATH, FileAccess.READ)
	if not file:
		return config

	var text := file.get_as_text()
	file.close()

	var json := JSON.new()
	var err := json.parse(text)
	if err == OK and json.data is Dictionary:
		var data: Dictionary = json.data
		if data.has("endpoint") and not str(data["endpoint"]).is_empty():
			config["endpoint"] = str(data["endpoint"])
		if data.has("model") and not str(data["model"]).is_empty():
			config["model"] = str(data["model"])
		if data.has("api_key"):
			config["api_key"] = str(data["api_key"])

	return config

static func save_config(endpoint: String, model: String, api_key: String) -> bool:
	var clean_endpoint := endpoint.strip_edges()
	if clean_endpoint.is_empty():
		clean_endpoint = DEFAULT_ENDPOINT

	var clean_model := model.strip_edges()
	if clean_model.is_empty():
		clean_model = DEFAULT_MODEL

	var data := {
		"endpoint": clean_endpoint,
		"model": clean_model,
		"api_key": api_key.strip_edges()
	}

	var file := FileAccess.open(CONFIG_PATH, FileAccess.WRITE)
	if not file:
		push_error("[VOID REALMS AI] Failed to open config file for writing: " + CONFIG_PATH)
		return false

	var json_string := JSON.stringify(data, "  ")
	file.store_string(json_string)
	file.close()
	return true

static func mask_api_key(api_key: String) -> String:
	var trimmed := api_key.strip_edges()
	if trimmed.is_empty():
		return "(API Anahtarı Ayarlanmadı)"
	if trimmed.length() <= 8:
		return "********"
	return trimmed.substr(0, 4) + "..." + trimmed.substr(trimmed.length() - 4)
