@tool
extends RefCounted
class_name VoidRealmsAIFileManager

# Returns a formatted summary of project structure and script content for DeepSeek system context
static func scan_project_context(max_files: int = 15) -> String:
	var context := "VOID REALMS Godot Project Summary:\n"
	var files := _list_res_files("res://scripts")
	files.append_array(_list_res_files("res://scenes"))

	var count := 0
	for path in files:
		if count >= max_files:
			break
		if path.ends_with(".gd") or path.ends_with(".tscn"):
			context += "\n--- File: " + path + " ---\n"
			var content := read_file(path)
			if content.length() > 2000:
				content = content.substr(0, 2000) + "\n... [truncated]"
			context += content + "\n"
			count += 1

	return context

static func _list_res_files(dir_path: String) -> Array:
	var result := []
	var dir := DirAccess.open(dir_path)
	if not dir:
		return result

	dir.list_dir_begin()
	var file_name := dir.get_next()
	while not file_name.is_empty():
		if not file_name.begins_with("."):
			var full_path := dir_path + "/" + file_name
			if dir.current_is_dir():
				result.append_array(_list_res_files(full_path))
			else:
				result.append(full_path)
		file_name = dir.get_next()
	dir.list_dir_end()
	return result

static func read_file(filepath: String) -> String:
	if not FileAccess.file_exists(filepath):
		return ""
	var file := FileAccess.open(filepath, FileAccess.READ)
	if not file:
		return ""
	var text := file.get_as_text()
	file.close()
	return text

# Parses code block suggestions from AI response text
# Looks for ```gdscript or ``` blocks or [FILE: res://...] annotations
static func parse_proposed_changes(ai_response: String) -> Array:
	var proposals := []
	var lines := ai_response.split("\n")

	var current_file := ""
	var in_code_block := false
	var code_buffer := ""

	for i in range(lines.size()):
		var line := lines[i]

		if line.strip_edges().begins_with("# File:") or line.strip_edges().begins_with("// File:") or line.strip_edges().begins_with("File:"):
			var parts := line.split(":")
			if parts.size() >= 2:
				current_file = parts[1].strip_edges()

		if line.strip_edges().begins_with("```"):
			if in_code_block:
				in_code_block = false
				if not current_file.is_empty() and not code_buffer.is_empty():
					proposals.append({
						"filepath": current_file,
						"new_content": code_buffer,
						"original_content": read_file(current_file)
					})
				current_file = ""
				code_buffer = ""
			else:
				in_code_block = true
				code_buffer = ""
				# Check if file path was on line right before
				if current_file.is_empty() and i > 0:
					var prev := lines[i - 1].strip_edges()
					if prev.begins_with("res://"):
						current_file = prev
			continue

		if in_code_block:
			code_buffer += line + "\n"

	return proposals

# Safe file modification method: ALWAYS creates backup first, NEVER executes arbitrary code
static func apply_file_change_with_user_approval(filepath: String, new_content: String) -> Dictionary:
	var result := {
		"success": false,
		"backup_id": "",
		"error": ""
	}

	# Safety check: Prevent modifying files outside res://
	if not filepath.begins_with("res://"):
		result["error"] = "Güvenlik Engeli: Sadece project 'res://' dizini altındaki dosyalar değiştirilebilir."
		return result

	# 1. Create backup before modification
	var backup_res := VoidRealmsAIBackupManager.create_backup(filepath)
	if not backup_res.get("success", false):
		result["error"] = "Yedekleme başarısız olduğu için değişiklik uygulanmadı: " + str(backup_res.get("error", ""))
		return result

	result["backup_id"] = str(backup_res.get("backup_id", ""))

	# 2. Write new content to res:// target file
	var dir_path := filepath.get_base_dir()
	if not DirAccess.dir_exists_absolute(dir_path):
		DirAccess.make_dir_recursive_absolute(dir_path)

	var file := FileAccess.open(filepath, FileAccess.WRITE)
	if not file:
		result["error"] = "Dosyaya yazılamadı: " + filepath
		return result

	file.store_string(new_content)
	file.close()

	result["success"] = true
	return result
