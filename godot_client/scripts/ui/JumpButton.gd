# Roblox-Style Touch Jump Button for Mobile Control (VOID REALMS)
# Positioned at bottom right of screen. Calls player.trigger_jump() on touch without camera interference.

extends Control

@export var player_node_path: NodePath = "^../../Player3D"
@export var button_radius: float = 48.0
@export var bg_color: Color = Color(0.12, 0.15, 0.25, 0.75)
@export var border_color: Color = Color(0.0, 0.85, 1.0, 0.8)
@export var press_color: Color = Color(0.0, 0.85, 1.0, 0.4)

var player: Player3DController
var is_pressed: bool = false
var touch_index: int = -1

func _ready() -> void:
	visible = true
	player = get_node_or_null(player_node_path) as Player3DController
	queue_redraw()

func _draw() -> void:
	var center = size * 0.5
	var color = press_color if is_pressed else bg_color

	# Draw Circular Touch Jump Button
	draw_circle(center, button_radius, color)
	draw_arc(center, button_radius, 0, TAU, 32, border_color, 2.5)

	# Draw "JUMP" Text
	var font = ThemeDB.fallback_font
	var font_size = 18
	var text = "JUMP"
	var text_size = font.get_string_size(text, HORIZONTAL_ALIGNMENT_CENTER, -1, font_size)
	var text_pos = center + Vector3(0, 0, 0) - Vector2(text_size.x * 0.5, -text_size.y * 0.3)
	draw_string(font, text_pos, text, HORIZONTAL_ALIGNMENT_CENTER, -1, font_size, Color.WHITE)

func _gui_input(event: InputEvent) -> void:
	if event is InputEventScreenTouch:
		if event.pressed and touch_index == -1:
			touch_index = event.index
			is_pressed = true
			_do_jump()
			accept_event()
			queue_redraw()
		elif not event.pressed and event.index == touch_index:
			touch_index = -1
			is_pressed = false
			accept_event()
			queue_redraw()

	elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		if event.pressed and touch_index == -1:
			touch_index = 999
			is_pressed = true
			_do_jump()
			accept_event()
			queue_redraw()
		elif not event.pressed and touch_index == 999:
			touch_index = -1
			is_pressed = false
			accept_event()
			queue_redraw()

func _do_jump() -> void:
	if not player:
		player = get_node_or_null(player_node_path) as Player3DController
	if player:
		player.trigger_jump()
