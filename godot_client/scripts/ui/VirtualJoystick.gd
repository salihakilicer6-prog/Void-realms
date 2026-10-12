# Roblox-Style Dynamic (Floating) Virtual Joystick (VOID REALMS)
# Covers left movement area of screen. Centers at touch location, follows finger drag, and resets on release.

extends Control
class_name VoidRealmsVirtualJoystick

@export var base_radius: float = 65.0
@export var knob_radius: float = 28.0
@export var max_distance: float = 55.0

# Base & Knob Colors
@export var base_color: Color = Color(0.1, 0.12, 0.2, 0.5)
@export var base_outline_color: Color = Color(0.0, 0.85, 1.0, 0.7)
@export var knob_color: Color = Color(0.0, 0.85, 1.0, 0.8)

var output_vector: Vector2 = Vector2.ZERO
var knob_position: Vector2 = Vector2.ZERO
var dynamic_center: Vector2 = Vector2.ZERO
var touch_index: int = -1
var is_active: bool = false

func _ready() -> void:
	visible = true
	_apply_layout()
	queue_redraw()

func _apply_layout() -> void:
	# Dynamic mode covers left movement area of screen
	anchors_preset = Control.PRESET_FULL_RECT
	anchor_left = 0.0
	anchor_top = 0.0
	anchor_right = 0.5
	anchor_bottom = 1.0
	offset_left = 0.0
	offset_top = 0.0
	offset_right = 0.0
	offset_bottom = 0.0
	mouse_filter = Control.MOUSE_FILTER_STOP

func _draw() -> void:
	if not is_active:
		return # Hide base and knob when joystick is not active

	var local_center: Vector2 = get_global_transform().affine_inverse() * dynamic_center

	# Draw Joystick Base
	draw_circle(local_center, base_radius, base_color)
	draw_arc(local_center, base_radius, 0, TAU, 32, base_outline_color, 2.0)

	# Draw Joystick Knob
	draw_circle(local_center + knob_position, knob_radius, knob_color)

func _gui_input(event: InputEvent) -> void:
	# DYNAMIC JOYSTICK GUI HANDLER (Captured within left movement Control rect)
	if event is InputEventScreenTouch:
		if event.pressed and touch_index == -1:
			touch_index = event.index
			dynamic_center = event.position
			_update_knob_from_center(event.position, dynamic_center)
			accept_event()
		elif not event.pressed and event.index == touch_index:
			_reset_joystick()
			accept_event()

	elif event is InputEventScreenDrag and event.index == touch_index:
		_update_knob_from_center(event.position, dynamic_center)
		accept_event()

	elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		if event.pressed and touch_index == -1:
			touch_index = 999
			dynamic_center = event.position
			_update_knob_from_center(event.position, dynamic_center)
			accept_event()
		elif not event.pressed and touch_index == 999:
			_reset_joystick()
			accept_event()

	elif event is InputEventMouseMotion and touch_index == 999:
		_update_knob_from_center(event.position, dynamic_center)
		accept_event()

func _input(event: InputEvent) -> void:
	# Global fallback for touch drags moving outside the Control bounds
	if touch_index != -1 and touch_index != 999:
		if event is InputEventScreenDrag and event.index == touch_index:
			_update_knob_from_center(event.position, dynamic_center)
			get_viewport().set_input_as_handled()
		elif event is InputEventScreenTouch and not event.pressed and event.index == touch_index:
			_reset_joystick()
			get_viewport().set_input_as_handled()

	elif touch_index == 999:
		if event is InputEventMouseMotion:
			_update_knob_from_center(event.position, dynamic_center)
		elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and not event.pressed:
			_reset_joystick()

func _update_knob_from_center(pos: Vector2, center_pos: Vector2) -> void:
	var offset = pos - center_pos
	if offset.length() > max_distance:
		knob_position = offset.normalized() * max_distance
	else:
		knob_position = offset

	output_vector = knob_position / max_distance
	is_active = true
	queue_redraw()

func _reset_joystick() -> void:
	touch_index = -1
	knob_position = Vector2.ZERO
	output_vector = Vector2.ZERO
	is_active = false
	queue_redraw()

func get_output() -> Vector2:
	return output_vector
