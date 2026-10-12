# Joystick Mode Switch Button (VOID REALMS)
# Toggles between Roblox-Style Dynamic (Floating) Joystick and Fixed Joystick modes.

extends Button

@export var joystick_path: NodePath = "^../VirtualJoystick"
var joystick: VoidRealmsVirtualJoystick

func _ready() -> void:
	joystick = get_node_or_null(joystick_path) as VoidRealmsVirtualJoystick
	if joystick:
		joystick.mode_changed.connect(_on_mode_changed)
		_update_button_text(joystick.is_dynamic_mode)
	pressed.connect(_on_button_pressed)

func _on_button_pressed() -> void:
	if joystick:
		joystick.toggle_mode()

func _on_mode_changed(is_dynamic: bool) -> void:
	_update_button_text(is_dynamic)

func _update_button_text(is_dynamic: bool) -> void:
	text = "Controls: Dynamic Joystick" if is_dynamic else "Controls: Fixed Joystick"
