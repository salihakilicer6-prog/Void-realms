@tool
extends EditorPlugin

var dock_instance: Control

func _enter_tree() -> void:
	# Enable plugin panel when running in editor mode
	if Engine.is_editor_hint():
		var dock_script := load("res://addons/void_realms_ai/ai_dock.gd")
		if dock_script:
			dock_instance = dock_script.new()
			add_control_to_bottom_panel(dock_instance, "VOID REALMS AI")
			print("[VOID REALMS AI] Plugin loaded successfully in Godot Editor.")

func _exit_tree() -> void:
	if dock_instance:
		remove_control_from_bottom_panel(dock_instance)
		dock_instance.free()
		print("[VOID REALMS AI] Plugin unloaded.")
