# Two-Option Joystick Mode Selector UI (VOID REALMS)
# Features dedicated [ FIXED ] and [ DYNAMIC ] buttons with active visual state highlights.

extends HBoxContainer

@export var joystick_path: NodePath = "^../VirtualJoystick"
var joystick: VoidRealmsVirtualJoystick

var fixed_button: Button
var dynamic_button: Button

func _ready() -> void:
	# Ensure touch/click input on selector buttons does not leak into camera rotation
	mouse_filter = Control.MOUSE_FILTER_STOP
	_create_selector_buttons()

	joystick = get_node_or_null(joystick_path) as VoidRealmsVirtualJoystick
	if joystick:
		joystick.mode_changed.connect(_on_mode_changed)
		_update_visual_states(joystick.is_dynamic_mode)

func _create_selector_buttons() -> void:
	fixed_button = Button.new()
	fixed_button.name = "FixedButton"
	fixed_button.text = "FIXED JOYSTICK"
	fixed_button.custom_minimum_size = Vector2(130, 38)
	fixed_button.pressed.connect(func(): _set_mode(false))
	add_child(fixed_button)

	dynamic_button = Button.new()
	dynamic_button.name = "DynamicButton"
	dynamic_button.text = "DYNAMIC JOYSTICK"
	dynamic_button.custom_minimum_size = Vector2(140, 38)
	dynamic_button.pressed.connect(func(): _set_mode(true))
	add_child(dynamic_button)

func _set_mode(is_dynamic: bool) -> void:
	if not joystick:
		joystick = get_node_or_null(joystick_path) as VoidRealmsVirtualJoystick
	if joystick:
		joystick.set_dynamic_mode(is_dynamic)

func _on_mode_changed(is_dynamic: bool) -> void:
	_update_visual_states(is_dynamic)

func _update_visual_states(is_dynamic: bool) -> void:
	if fixed_button and dynamic_button:
		if is_dynamic:
			fixed_button.text = "FIXED"
			dynamic_button.text = "[ DYNAMIC ACTIVE ]"
		else:
			fixed_button.text = "[ FIXED ACTIVE ]"
			dynamic_button.text = "DYNAMIC"
