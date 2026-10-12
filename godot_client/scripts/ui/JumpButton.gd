# Roblox-Style Touch Jump Button for Mobile Control (VOID REALMS)
# Positioned at bottom right of screen. Calls player.trigger_jump() on touch without camera interference.
# Features smooth press-down and release scale animation.

extends Control

@export var player_node_path: NodePath = "^../../Player3D"
@export var button_radius: float = 48.0
@export var bg_color: Color = Color(0.12, 0.15, 0.25, 0.75)
@export var border_color: Color = Color(0.0, 0.85, 1.0, 0.8)
@export var press_color: Color = Color(0.0, 0.85, 1.0, 0.4)

var player: Player3DController
var is_pressed: bool = false
var touch_index: int = -1
var current_scale: float = 1.0

func _ready() -> void:
	visible = true
	set_process(true)
	player = get_node_or_null(player_node_path) as Player3DController
	queue_redraw()

func _process(_delta: float) -> void:
	var target_scale = 0.88 if is_pressed else 1.0
	if not is_equal_approx(current_scale, target_scale):
		current_scale = lerp(current_scale, target_scale, 0.3)
		queue_redraw()

func _draw() -> void:
	var center = size * 0.5
	var animated_radius = button_radius * current_scale
	var color = press_color if is_pressed else bg_color

	# Draw Circular Touch Jump Button with smooth animated scale
	draw_circle(center, animated_radius, color)
	draw_arc(center, animated_radius, 0, TAU, 32, border_color, 2.5 * current_scale)

	# Draw "JUMP" Text
	var font = ThemeDB.fallback_font
	var font_size = int(18 * current_scale)
	var text = "JUMP"
	var text_size = font.get_string_size(text, HORIZONTAL_ALIGNMENT_CENTER, -1, font_size)
	var text_pos = center - Vector2(text_size.x * 0.5, -text_size.y * 0.3)
	draw_string(font, text_pos, text, HORIZONTAL_ALIGNMENT_CENTER, -1, font_size, Color.WHITE)

func _gui_input(event: InputEvent) -> void:
	if event is InputEventScreenTouch:
		if event.pressed and touch_index == -1:
			touch_index = event.index
			is_pressed = true
			_do_jump()
			accept_event()
		elif not event.pressed and event.index == touch_index:
			touch_index = -1
			is_pressed = false
			accept_event()

	elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		if event.pressed and touch_index == -1:
			touch_index = 999
			is_pressed = true
			_do_jump()
			accept_event()
		elif not event.pressed and touch_index == 999:
			touch_index = -1
			is_pressed = false
			accept_event()

func _do_jump() -> void:
	if not player:
		player = get_node_or_null(player_node_path) as Player3DController
	if player:
		player.trigger_jump()
