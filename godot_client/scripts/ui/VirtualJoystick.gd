# Virtual Joystick for Touch / Android & Desktop testing
# Godot 4 3D Prototype (VOID REALMS)

extends Control
class_name VirtualJoystick

@export var base_radius: float = 65.0
@export var knob_radius: float = 28.0
@export var max_distance: float = 55.0

# Base & Knob Colors
@export var base_color: Color = Color(0.1, 0.12, 0.2, 0.5)
@export var base_outline_color: Color = Color(0.0, 0.8, 1.0, 0.7)
@export var knob_color: Color = Color(0.0, 0.85, 1.0, 0.8)

var output_vector: Vector2 = Vector2.ZERO
var knob_position: Vector2 = Vector2.ZERO
var touch_index: int = -1
var is_active: bool = false

func _ready() -> void:
	# Ensure the control receives input and stays visible
	visible = true
	queue_redraw()

func _draw() -> void:
	var center = size * 0.5
	# Draw Joystick Base
	draw_circle(center, base_radius, base_color)
	draw_arc(center, base_radius, 0, TAU, 32, base_outline_color, 2.0)

	# Draw Joystick Knob
	draw_circle(center + knob_position, knob_radius, knob_color)

func _gui_input(event: InputEvent) -> void:
	if event is InputEventScreenTouch:
		if event.pressed and touch_index == -1:
			touch_index = event.index
			_update_knob_from_local_pos(event.position)
			accept_event()
		elif not event.pressed and event.index == touch_index:
			_reset_joystick()
			accept_event()

	elif event is InputEventScreenDrag and event.index == touch_index:
		_update_knob_from_local_pos(event.position)
		accept_event()

	elif event is InputEventMouseButton:
		if event.button_index == MOUSE_BUTTON_LEFT:
			if event.pressed and touch_index == -1:
				touch_index = 999
				_update_knob_from_local_pos(event.position)
				accept_event()
			elif not event.pressed and touch_index == 999:
				_reset_joystick()
				accept_event()

	elif event is InputEventMouseMotion and touch_index == 999:
		_update_knob_from_local_pos(event.position)
		accept_event()

func _input(event: InputEvent) -> void:
	# Global input fallback to handle touches/drags that move outside control bounds
	if touch_index != -1 and touch_index != 999:
		if event is InputEventScreenDrag and event.index == touch_index:
			var local_pos = get_global_transform().affine_inverse() * event.position
			_update_knob_from_local_pos(local_pos)
			get_viewport().set_input_as_handled()
		elif event is InputEventScreenTouch and not event.pressed and event.index == touch_index:
			_reset_joystick()
			get_viewport().set_input_as_handled()

	elif touch_index == 999:
		if event is InputEventMouseMotion:
			var local_pos = get_global_transform().affine_inverse() * event.position
			_update_knob_from_local_pos(local_pos)
		elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and not event.pressed:
			_reset_joystick()

func _update_knob_from_local_pos(local_pos: Vector2) -> void:
	var center = size * 0.5
	var offset = local_pos - center
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
