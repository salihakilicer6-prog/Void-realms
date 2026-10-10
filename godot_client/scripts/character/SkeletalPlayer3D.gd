# 3D Skeletal Character Controller for Godot 4.3 Client (VOID REALMS V7)
# License: MIT License (Open Source 3D Character Controller Architecture)

extends CharacterBody3D
class_name SkeletalPlayer3D

@export var speed: float = 6.0
@export var jump_velocity: float = 8.0

var gravity: float = ProjectSettings.get_setting("physics/3d/default_gravity")
@onready var skeleton: Skeleton3D = $Skeleton3D
@onready var anim_player: AnimationPlayer = $AnimationPlayer

func _physics_process(delta: float) -> void:
	if not is_on_floor():
		velocity.y -= gravity * delta

	if Input.is_action_just_pressed("ui_accept") and is_on_floor():
		velocity.y = jump_velocity

	var input_dir = Input.get_vector("ui_left", "ui_right", "ui_up", "ui_down")
	var direction = (transform.basis * Vector3(input_dir.x, 0, input_dir.y)).normalized()

	if direction:
		velocity.x = direction.x * speed
		velocity.z = direction.z * speed
		if anim_player and anim_player.has_animation("Walk"):
			anim_player.play("Walk")
	else:
		velocity.x = move_toward(velocity.x, 0, speed)
		velocity.z = move_toward(velocity.z, 0, speed)
		if anim_player and anim_player.has_animation("Idle"):
			anim_player.play("Idle")

	move_and_slide()
