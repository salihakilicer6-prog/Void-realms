# Godot 4 3D Character Controller for Technical Prototype (VOID REALMS)
# Handles WASD keyboard movement, 3rd person mouse camera control, and Android touch support.

extends CharacterBody3D
class_name Player3DController

@export var speed: float = 7.0
@export var jump_velocity: float = 8.5
@export var mouse_sensitivity: float = 0.003

var gravity: float = ProjectSettings.get_setting("physics/3d/default_gravity", 9.8)

@onready var spring_arm: SpringArm3D = $SpringArm3D
@onready var mesh_instance: MeshInstance3D = $MeshInstance3D

var touch_drag_vector := Vector2.ZERO
var is_touch_active := false

func _ready() -> void:
	Input.set_mouse_mode(Input.MOUSE_MODE_CAPTURED)

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseMotion and Input.get_mouse_mode() == Input.MOUSE_MODE_CAPTURED:
		rotate_y(-event.relative.x * mouse_sensitivity)
		if spring_arm:
			spring_arm.rotate_x(-event.relative.y * mouse_sensitivity)
			spring_arm.rotation.x = clamp(spring_arm.rotation.x, deg_to_rad(-75), deg_to_rad(30))

	# Toggle mouse capture with ESC
	if event.is_action_pressed("ui_cancel"):
		if Input.get_mouse_mode() == Input.MOUSE_MODE_CAPTURED:
			Input.set_mouse_mode(Input.MOUSE_MODE_VISIBLE)
		else:
			Input.set_mouse_mode(Input.MOUSE_MODE_CAPTURED)

	# Android Touch Camera Orbit Support
	if event is InputEventScreenDrag:
		rotate_y(-event.relative.x * mouse_sensitivity * 1.5)
		if spring_arm:
			spring_arm.rotate_x(-event.relative.y * mouse_sensitivity * 1.5)
			spring_arm.rotation.x = clamp(spring_arm.rotation.x, deg_to_rad(-75), deg_to_rad(30))

func _physics_process(delta: float) -> void:
	if not is_on_floor():
		velocity.y -= gravity * delta

	if Input.is_action_just_pressed("jump") and is_on_floor():
		velocity.y = jump_velocity

	# Get WASD or Touch Input
	var input_dir = Vector2.ZERO
	if Input.is_action_pressed("move_forward"):
		input_dir.y -= 1.0
	if Input.is_action_pressed("move_backward"):
		input_dir.y += 1.0
	if Input.is_action_pressed("move_left"):
		input_dir.x -= 1.0
	if Input.is_action_pressed("move_right"):
		input_dir.x += 1.0

	if touch_drag_vector != Vector2.ZERO:
		input_dir = touch_drag_vector

	input_dir = input_dir.normalized()

	var direction = (transform.basis * Vector3(input_dir.x, 0, input_dir.y)).normalized()

	if direction:
		velocity.x = direction.x * speed
		velocity.z = direction.z * speed
	else:
		velocity.x = move_toward(velocity.x, 0, speed)
		velocity.z = move_toward(velocity.z, 0, speed)

	move_and_slide()

func set_touch_vector(vec: Vector2) -> void:
	touch_drag_vector = vec
