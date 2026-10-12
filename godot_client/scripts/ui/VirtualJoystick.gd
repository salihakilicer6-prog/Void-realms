# Virtual Joystick for Touch / Android & Desktop testing (VOID REALMS)
# Supports both Roblox-style Dynamic (Floating) Joystick and Fixed Joystick modes.
# Features persistent settings via ConfigFile.

extends Control
class_name VoidRealmsVirtualJoystick

signal mode_changed(is_dynamic: bool)

@export var is_dynamic_mode: bool = false
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

const CONFIG_PATH := "user://joystick_config.cfg"

func _ready() -> void:
	visible = true
	_load_mode_setting()
	_apply_mode_layout()
	queue_redraw()

func _load_mode_setting() -> void:
	var cfg = ConfigFile.new()
	if cfg.load(CONFIG_PATH) == OK:
		is_dynamic_mode = cfg.get_value("controls", "dynamic_joystick", false)

func _save_mode_setting() -> void:
	var cfg = ConfigFile.new()
	cfg.set_value("controls", "dynamic_joystick", is_dynamic_mode)
	cfg.save(CONFIG_PATH)

func set_dynamic_mode(enabled: bool) -> void:
	is_dynamic_mode = enabled
	_save_mode_setting()
	_reset_joystick()
	_apply_mode_layout()
	emit_signal("mode_changed", is_dynamic_mode)
	queue_redraw()

func _apply_mode_layout() -> void:
	if is_dynamic_mode:
		# Dynamic mode covers left movement area
		anchors_preset = Control.PRESET_FULL_RECT
		anchor_left = 0.0
		anchor_top = 0.0
		anchor_right = 0.5
		anchor_bottom = 1.0
		offset_left = 0.0
		offset_top = 0.0
		offset_right = 0.0
		offset_bottom = 0.0
	else:
		# Fixed mode anchored at bottom-left
		anchors_preset = Control.PRESET_BOTTOM_LEFT
		anchor_left = 0.0
		anchor_top = 1.0
		anchor_right = 0.0
		anchor_bottom = 1.0
		offset_left = 40.0
		offset_top = -200.0
		offset_right = 200.0
		offset_bottom = -40.0

func toggle_mode() -> void:
	set_dynamic_mode(not is_dynamic_mode)

func _draw() -> void:
	if not is_active and is_dynamic_mode:
		return # Hide in dynamic mode when inactive

	var local_center: Vector2
	if is_dynamic_mode:
		local_center = get_global_transform().affine_inverse() * dynamic_center
	else:
		local_center = size * 0.5

	# Draw Joystick Base
	draw_circle(local_center, base_radius, base_color)
	draw_arc(local_center, base_radius, 0, TAU, 32, base_outline_color, 2.0)

	# Draw Joystick Knob
	draw_circle(local_center + knob_position, knob_radius, knob_color)

func _gui_input(event: InputEvent) -> void:
	if is_dynamic_mode:
		# DYNAMIC MODE GUI HANDLER (Captured within left movement Control rect)
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

	else:
		# FIXED MODE HANDLER
		if event is InputEventScreenTouch:
			if event.pressed and touch_index == -1:
				touch_index = event.index
				_update_knob_from_center(event.position, size * 0.5)
				accept_event()
			elif not event.pressed and event.index == touch_index:
				_reset_joystick()
				accept_event()

		elif event is InputEventScreenDrag and event.index == touch_index:
			_update_knob_from_center(event.position, size * 0.5)
			accept_event()

		elif event is InputEventMouseButton:
			if event.button_index == MOUSE_BUTTON_LEFT:
				if event.pressed and touch_index == -1:
					touch_index = 999
					_update_knob_from_center(event.position, size * 0.5)
					accept_event()
				elif not event.pressed and touch_index == 999:
					_reset_joystick()
					accept_event()

		elif event is InputEventMouseMotion and touch_index == 999:
			_update_knob_from_center(event.position, size * 0.5)
			accept_event()

func _input(event: InputEvent) -> void:
	# Global fallback for touch drags moving outside the Control bounds
	if touch_index != -1 and touch_index != 999:
		if event is InputEventScreenDrag and event.index == touch_index:
			var center_pos = dynamic_center if is_dynamic_mode else size * 0.5
			var pos = event.position if is_dynamic_mode else (get_global_transform().affine_inverse() * event.position)
			_update_knob_from_center(pos, center_pos)
			get_viewport().set_input_as_handled()
		elif event is InputEventScreenTouch and not event.pressed and event.index == touch_index:
			_reset_joystick()
			get_viewport().set_input_as_handled()

	elif touch_index == 999:
		if event is InputEventMouseMotion:
			var center_pos = dynamic_center if is_dynamic_mode else size * 0.5
			var pos = event.position if is_dynamic_mode else (get_global_transform().affine_inverse() * event.position)
			_update_knob_from_center(pos, center_pos)
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
