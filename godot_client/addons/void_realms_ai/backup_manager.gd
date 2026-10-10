@tool
extends RefCounted
class_name VoidRealmsAIBackupManager

const BACKUP_DIR: String = "user://void_realms_ai_backups/"
const MANIFEST_PATH: String = "user://void_realms_ai_backups/backup_manifest.json"

static func _ensure_backup_dir() -> void:
	if not DirAccess.dir_exists_absolute(BACKUP_DIR):
		DirAccess.make_dir_recursive_absolute(BACKUP_DIR)

static func _load_manifest() -> Array:
	_ensure_backup_dir()
	if not FileAccess.file_exists(MANIFEST_PATH):
		return []

	var file := FileAccess.open(MANIFEST_PATH, FileAccess.READ)
	if not file:
		return []

	var text := file.get_as_text()
	file.close()

	var json := JSON.new()
	var err := json.parse(text)
	if err == OK and json.data is Array:
		return json.data
	return []

static func _save_manifest(manifest: Array) -> void:
	_ensure_backup_dir()
	var file := FileAccess.open(MANIFEST_PATH, FileAccess.WRITE)
	if not file:
		return
	file.store_string(JSON.stringify(manifest, "  "))
	file.close()

static func create_backup(target_res_path: String) -> Dictionary:
	_ensure_backup_dir()
	var result := {
		"success": false,
		"backup_id": "",
		"error": ""
	}

	if not FileAccess.file_exists(target_res_path):
		# Target file doesn't exist yet (e.g. new file proposed by AI)
		result["success"] = true
		result["backup_id"] = "new_file"
		return result

	var target_file := FileAccess.open(target_res_path, FileAccess.READ)
	if not target_file:
		result["error"] = "Hedef dosya okunamadı: " + target_res_path
		return result

	var content := target_file.get_as_text()
	target_file.close()

	var datetime := Time.get_datetime_dict_from_system()
	var timestamp_str := "%04d%02d%02d_%02d%02d%02d" % [
		datetime["year"], datetime["month"], datetime["day"],
		datetime["hour"], datetime["minute"], datetime["second"]
	]

	var filename_clean := target_res_path.get_file().replace(".", "_")
	var backup_filename := filename_clean + "_" + timestamp_str + ".bak"
	var backup_full_path := BACKUP_DIR + backup_filename
	var backup_id := "bak_" + timestamp_str + "_" + str(randi() % 10000)

	var backup_file := FileAccess.open(backup_full_path, FileAccess.WRITE)
	if not backup_file:
		result["error"] = "Yedek dosyası oluşturulamadı: " + backup_full_path
		return result

	backup_file.store_string(content)
	backup_file.close()

	var manifest := _load_manifest()
	var entry := {
		"id": backup_id,
		"target_path": target_res_path,
		"backup_file": backup_filename,
		"timestamp": Time.get_datetime_string_from_system(),
		"size": content.length()
	}
	manifest.push_front(entry) # Latest backups first
	_save_manifest(manifest)

	result["success"] = true
	result["backup_id"] = backup_id
	return result

static func list_backups() -> Array:
	return _load_manifest()

static func rollback_backup(backup_id: String) -> Dictionary:
	var result := {
		"success": false,
		"target_path": "",
		"error": ""
	}

	var manifest := _load_manifest()
	var target_entry: Dictionary = {}

	for entry in manifest:
		if entry is Dictionary and entry.get("id", "") == backup_id:
			target_entry = entry
			break

	if target_entry.is_empty():
		result["error"] = "Yedek kaydı bulunamadı (ID: " + backup_id + ")."
		return result

	var backup_filename: String = target_entry.get("backup_file", "")
	var target_path: String = target_entry.get("target_path", "")
	var backup_full_path := BACKUP_DIR + backup_filename

	if not FileAccess.file_exists(backup_full_path):
		result["error"] = "Yedek dosyası disk üzerinde bulunamadı: " + backup_full_path
		return result

	var backup_file := FileAccess.open(backup_full_path, FileAccess.READ)
	if not backup_file:
		result["error"] = "Yedek dosyası okunamadı."
		return result

	var backup_content := backup_file.get_as_text()
	backup_file.close()

	# Restore content to res:// target path
	var dest_file := FileAccess.open(target_path, FileAccess.WRITE)
	if not dest_file:
		result["error"] = "Hedef dosyaya yazılamadı: " + target_path
		return result

	dest_file.store_string(backup_content)
	dest_file.close()

	result["success"] = true
	result["target_path"] = target_path
	return result
