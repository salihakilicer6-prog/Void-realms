# Shadow Warrior Protagonist 3D Character Model & Procedural Animation System (VOID REALMS)
# High-quality dark fantasy sci-fi anime protagonist (Shadow Warrior)
# Proportions: Athletic human (1.8m height), matte black long coat, dark armor, silver & deep-green accents.
# Head Design: Full cohesive dark metal armored helmet with closed faceplate and deep-green visor.

extends Node3D
class_name ShadowWarriorModel

# Procedural Animation States
enum AnimState {
	IDLE,
	WALK,
	RUN,
	JUMP,
	FALL
}

# Node references initialized in _build_character_mesh()
var body_pivot: Node3D
var torso: Node3D
var head_pivot: Node3D
var left_arm_pivot: Node3D
var right_arm_pivot: Node3D
var left_forearm_pivot: Node3D
var right_forearm_pivot: Node3D
var left_leg_pivot: Node3D
var right_leg_pivot: Node3D
var left_shin_pivot: Node3D
var right_shin_pivot: Node3D
var coat_tail_left: Node3D
var coat_tail_right: Node3D

# Materials
var mat_coat: StandardMaterial3D
var mat_armor: StandardMaterial3D
var mat_silver: StandardMaterial3D
var mat_green_glow: StandardMaterial3D

# Animation State Variables
var anim_time: float = 0.0
var state_time: float = 0.0
var current_state: AnimState = AnimState.IDLE
var parent_character: CharacterBody3D

func _ready() -> void:
	_create_materials()
	_build_character_mesh()
	parent_character = get_parent() as CharacterBody3D

func get_current_state_name() -> String:
	match current_state:
		AnimState.IDLE: return "IDLE"
		AnimState.WALK: return "WALK"
		AnimState.RUN: return "RUN"
		AnimState.JUMP: return "JUMP"
		AnimState.FALL: return "FALL"
		_: return "UNKNOWN"

func _process(delta: float) -> void:
	anim_time += delta
	state_time += delta
	_update_procedural_animations(delta)

func _create_materials() -> void:
	# Matte-black long coat material
	mat_coat = StandardMaterial3D.new()
	mat_coat.albedo_color = Color(0.06, 0.06, 0.08)
	mat_coat.roughness = 0.85

	# Fitted dark armor material
	mat_armor = StandardMaterial3D.new()
	mat_armor.albedo_color = Color(0.12, 0.14, 0.18)
	mat_armor.metallic = 0.75
	mat_armor.roughness = 0.3

	# Dark silver trim details material
	mat_silver = StandardMaterial3D.new()
	mat_silver.albedo_color = Color(0.48, 0.50, 0.55)
	mat_silver.metallic = 0.85
	mat_silver.roughness = 0.2

	# Restrained deep-green glowing accent lines
	mat_green_glow = StandardMaterial3D.new()
	mat_green_glow.albedo_color = Color(0.0, 0.65, 0.38)
	mat_green_glow.emission_enabled = true
	mat_green_glow.emission = Color(0.0, 0.55, 0.3)
	mat_green_glow.emission_energy_multiplier = 1.5

func _build_character_mesh() -> void:
	body_pivot = Node3D.new()
	body_pivot.name = "BodyPivot"
	add_child(body_pivot)

	# --- TORSO & CHEST ARMOR ---
	torso = Node3D.new()
	torso.name = "Torso"
	torso.position = Vector3(0, 0.95, 0)
	body_pivot.add_child(torso)

	# Fitted Chestplate (V-taper athletic torso)
	var chest_mesh = PrismMesh.new()
	chest_mesh.size = Vector3(0.52, 0.58, 0.32)
	var chest_inst = MeshInstance3D.new()
	chest_inst.mesh = chest_mesh
	chest_inst.rotation_degrees = Vector3(180, 0, 0) # V-shape apex pointing down to waist
	chest_inst.position = Vector3(0, 0.1, 0)
	chest_inst.set_surface_override_material(0, mat_armor)
	torso.add_child(chest_inst)

	# Deep Green Energy Core Line on Chest
	var energy_line_mesh = BoxMesh.new()
	energy_line_mesh.size = Vector3(0.04, 0.42, 0.05)
	var energy_line_inst = MeshInstance3D.new()
	energy_line_inst.mesh = energy_line_mesh
	energy_line_inst.position = Vector3(0, 0.1, -0.16)
	energy_line_inst.set_surface_override_material(0, mat_green_glow)
	torso.add_child(energy_line_inst)

	# High Coat Collar
	var collar_mesh = BoxMesh.new()
	collar_mesh.size = Vector3(0.32, 0.18, 0.28)
	var collar_inst = MeshInstance3D.new()
	collar_inst.mesh = collar_mesh
	collar_inst.position = Vector3(0, 0.4, 0.02)
	collar_inst.set_surface_override_material(0, mat_coat)
	torso.add_child(collar_inst)

	# Matte Black Coat Back / Vest
	var coat_back_mesh = BoxMesh.new()
	coat_back_mesh.size = Vector3(0.54, 0.62, 0.34)
	var coat_back_inst = MeshInstance3D.new()
	coat_back_inst.mesh = coat_back_mesh
	coat_back_inst.position = Vector3(0, 0.08, 0.01)
	coat_back_inst.set_surface_override_material(0, mat_coat)
	torso.add_child(coat_back_inst)

	# COAT TAILS (Left & Right Flaps extending down back)
	var tail_mesh = BoxMesh.new()
	tail_mesh.size = Vector3(0.24, 0.68, 0.04)

	coat_tail_left = Node3D.new()
	coat_tail_left.name = "CoatTailLeft"
	coat_tail_left.position = Vector3(-0.13, -0.22, 0.16)
	torso.add_child(coat_tail_left)
	var tail_l_inst = MeshInstance3D.new()
	tail_l_inst.mesh = tail_mesh
	tail_l_inst.position = Vector3(0, -0.34, 0)
	tail_l_inst.set_surface_override_material(0, mat_coat)
	coat_tail_left.add_child(tail_l_inst)

	coat_tail_right = Node3D.new()
	coat_tail_right.name = "CoatTailRight"
	coat_tail_right.position = Vector3(0.13, -0.22, 0.16)
	torso.add_child(coat_tail_right)
	var tail_r_inst = MeshInstance3D.new()
	tail_r_inst.mesh = tail_mesh
	tail_r_inst.position = Vector3(0, -0.34, 0)
	tail_r_inst.set_surface_override_material(0, mat_coat)
	coat_tail_right.add_child(tail_r_inst)

	# SHOULDER PAULDRONS
	var pauldron_mesh = PrismMesh.new()
	pauldron_mesh.size = Vector3(0.24, 0.22, 0.32)

	var pauldron_l = MeshInstance3D.new()
	pauldron_l.mesh = pauldron_mesh
	pauldron_l.position = Vector3(-0.32, 0.32, 0)
	pauldron_l.rotation_degrees = Vector3(0, 0, 25)
	pauldron_l.set_surface_override_material(0, mat_armor)
	torso.add_child(pauldron_l)

	var pauldron_r = MeshInstance3D.new()
	pauldron_r.mesh = pauldron_mesh
	pauldron_r.position = Vector3(0.32, 0.32, 0)
	pauldron_r.rotation_degrees = Vector3(0, 0, -25)
	pauldron_r.set_surface_override_material(0, mat_armor)
	torso.add_child(pauldron_r)

	# --- ARMORED HELMET (FULL CHARACTER HEAD) ---
	head_pivot = Node3D.new()
	head_pivot.name = "HeadPivot"
	head_pivot.position = Vector3(0, 0.44, 0)
	torso.add_child(head_pivot)

	# Main Helmet Dome Shell (Dark Metal)
	var helmet_mesh = BoxMesh.new()
	helmet_mesh.size = Vector3(0.28, 0.32, 0.30)
	var helmet_inst = MeshInstance3D.new()
	helmet_inst.mesh = helmet_mesh
	helmet_inst.position = Vector3(0, 0.16, 0)
	helmet_inst.set_surface_override_material(0, mat_armor)
	head_pivot.add_child(helmet_inst)

	# Closed Angular Faceplate
	var faceplate_mesh = PrismMesh.new()
	faceplate_mesh.size = Vector3(0.26, 0.28, 0.10)
	var faceplate_inst = MeshInstance3D.new()
	faceplate_inst.mesh = faceplate_mesh
	faceplate_inst.position = Vector3(0, 0.14, -0.16)
	faceplate_inst.rotation_degrees = Vector3(-90, 0, 0) # Angular visor slope
	faceplate_inst.set_surface_override_material(0, mat_armor)
	head_pivot.add_child(faceplate_inst)

	# Sleek Deep-Green Visor Strip
	var visor_mesh = BoxMesh.new()
	visor_mesh.size = Vector3(0.22, 0.05, 0.04)
	var visor_inst = MeshInstance3D.new()
	visor_inst.mesh = visor_mesh
	visor_inst.position = Vector3(0, 0.18, -0.16)
	visor_inst.set_surface_override_material(0, mat_green_glow)
	head_pivot.add_child(visor_inst)

	# Dark Silver Jaw & Neck Guard Plates
	var jaw_mesh = BoxMesh.new()
	jaw_mesh.size = Vector3(0.29, 0.12, 0.28)
	var jaw_inst = MeshInstance3D.new()
	jaw_inst.mesh = jaw_mesh
	jaw_inst.position = Vector3(0, 0.04, -0.02)
	jaw_inst.set_surface_override_material(0, mat_silver)
	head_pivot.add_child(jaw_inst)

	# Top Helmet Fin / Crest
	var crest_mesh = PrismMesh.new()
	crest_mesh.size = Vector3(0.04, 0.12, 0.24)
	var crest_inst = MeshInstance3D.new()
	crest_inst.mesh = crest_mesh
	crest_inst.position = Vector3(0, 0.36, -0.02)
	crest_inst.rotation_degrees = Vector3(-15, 0, 0)
	crest_inst.set_surface_override_material(0, mat_silver)
	head_pivot.add_child(crest_inst)

	# --- ARMS & GAUNTLETS ---
	# Left Arm
	left_arm_pivot = Node3D.new()
	left_arm_pivot.position = Vector3(-0.33, 0.30, 0)
	torso.add_child(left_arm_pivot)

	var upper_arm_mesh = BoxMesh.new()
	upper_arm_mesh.size = Vector3(0.16, 0.32, 0.16)
	var upper_arm_l = MeshInstance3D.new()
	upper_arm_l.mesh = upper_arm_mesh
	upper_arm_l.position = Vector3(0, -0.16, 0)
	upper_arm_l.set_surface_override_material(0, mat_coat)
	left_arm_pivot.add_child(upper_arm_l)

	left_forearm_pivot = Node3D.new()
	left_forearm_pivot.position = Vector3(0, -0.32, 0)
	left_arm_pivot.add_child(left_forearm_pivot)

	var forearm_mesh = BoxMesh.new()
	forearm_mesh.size = Vector3(0.15, 0.34, 0.15)
	var forearm_l = MeshInstance3D.new()
	forearm_l.mesh = forearm_mesh
	forearm_l.position = Vector3(0, -0.17, 0)
	forearm_l.set_surface_override_material(0, mat_armor)
	left_forearm_pivot.add_child(forearm_l)

	# Silver Gauntlet Knuckle Detail
	var gauntlet_detail = BoxMesh.new()
	gauntlet_detail.size = Vector3(0.16, 0.08, 0.16)
	var gauntlet_l = MeshInstance3D.new()
	gauntlet_l.mesh = gauntlet_detail
	gauntlet_l.position = Vector3(0, -0.22, 0)
	gauntlet_l.set_surface_override_material(0, mat_silver)
	left_forearm_pivot.add_child(gauntlet_l)

	# Right Arm
	right_arm_pivot = Node3D.new()
	right_arm_pivot.position = Vector3(0.33, 0.30, 0)
	torso.add_child(right_arm_pivot)

	var upper_arm_r = MeshInstance3D.new()
	upper_arm_r.mesh = upper_arm_mesh
	upper_arm_r.position = Vector3(0, -0.16, 0)
	upper_arm_r.set_surface_override_material(0, mat_coat)
	right_arm_pivot.add_child(upper_arm_r)

	right_forearm_pivot = Node3D.new()
	right_forearm_pivot.position = Vector3(0, -0.32, 0)
	right_arm_pivot.add_child(right_forearm_pivot)

	var forearm_r = MeshInstance3D.new()
	forearm_r.mesh = forearm_mesh
	forearm_r.position = Vector3(0, -0.17, 0)
	forearm_r.set_surface_override_material(0, mat_armor)
	right_forearm_pivot.add_child(forearm_r)

	var gauntlet_r = MeshInstance3D.new()
	gauntlet_r.mesh = gauntlet_detail
	gauntlet_r.position = Vector3(0, -0.22, 0)
	gauntlet_r.set_surface_override_material(0, mat_silver)
	right_forearm_pivot.add_child(gauntlet_r)

	# --- LEGS & HIGH ARMORED BOOTS ---
	var thigh_mesh = BoxMesh.new()
	thigh_mesh.size = Vector3(0.20, 0.42, 0.20)

	# Left Leg
	left_leg_pivot = Node3D.new()
	left_leg_pivot.position = Vector3(-0.14, 0.48, 0)
	body_pivot.add_child(left_leg_pivot)

	var thigh_l = MeshInstance3D.new()
	thigh_l.mesh = thigh_mesh
	thigh_l.position = Vector3(0, -0.21, 0)
	thigh_l.set_surface_override_material(0, mat_coat) # Dark trousers
	left_leg_pivot.add_child(thigh_l)

	left_shin_pivot = Node3D.new()
	left_shin_pivot.position = Vector3(0, -0.42, 0)
	left_leg_pivot.add_child(left_shin_pivot)

	var boot_mesh = BoxMesh.new()
	boot_mesh.size = Vector3(0.21, 0.44, 0.22)
	var boot_l = MeshInstance3D.new()
	boot_l.mesh = boot_mesh
	boot_l.position = Vector3(0, -0.22, -0.02)
	boot_l.set_surface_override_material(0, mat_armor)
	left_shin_pivot.add_child(boot_l)

	var shin_plate_mesh = BoxMesh.new()
	shin_plate_mesh.size = Vector3(0.18, 0.28, 0.04)
	var shin_plate_l = MeshInstance3D.new()
	shin_plate_l.mesh = shin_plate_mesh
	shin_plate_l.position = Vector3(0, -0.20, -0.12)
	shin_plate_l.set_surface_override_material(0, mat_silver)
	left_shin_pivot.add_child(shin_plate_l)

	# Right Leg
	right_leg_pivot = Node3D.new()
	right_leg_pivot.position = Vector3(0.14, 0.48, 0)
	body_pivot.add_child(right_leg_pivot)

	var thigh_r = MeshInstance3D.new()
	thigh_r.mesh = thigh_mesh
	thigh_r.position = Vector3(0, -0.21, 0)
	thigh_r.set_surface_override_material(0, mat_coat)
	right_leg_pivot.add_child(thigh_r)

	right_shin_pivot = Node3D.new()
	right_shin_pivot.position = Vector3(0, -0.42, 0)
	right_leg_pivot.add_child(right_shin_pivot)

	var boot_r = MeshInstance3D.new()
	boot_r.mesh = boot_mesh
	boot_r.position = Vector3(0, -0.22, -0.02)
	boot_r.set_surface_override_material(0, mat_armor)
	right_shin_pivot.add_child(boot_r)

	var shin_plate_r = MeshInstance3D.new()
	shin_plate_r.mesh = shin_plate_mesh
	shin_plate_r.position = Vector3(0, -0.20, -0.12)
	shin_plate_r.set_surface_override_material(0, mat_silver)
	right_shin_pivot.add_child(shin_plate_r)

func _update_procedural_animations(delta: float) -> void:
	if not parent_character:
		parent_character = get_parent() as CharacterBody3D
		if not parent_character:
			return

	var vel = parent_character.velocity
	var horiz_speed = Vector2(vel.x, vel.z).length()
	var is_floor = parent_character.is_on_floor()

	# Determine target state based on movement & grounded status
	var target_state: AnimState
	if not is_floor:
		if vel.y > 0.2:
			target_state = AnimState.JUMP
		elif vel.y < -0.2:
			target_state = AnimState.FALL
		else:
			# Apex of jump transition
			target_state = AnimState.JUMP if current_state == AnimState.JUMP else AnimState.FALL
	else:
		if horiz_speed > 4.5:
			target_state = AnimState.RUN
		elif horiz_speed > 0.1:
			target_state = AnimState.WALK
		else:
			target_state = AnimState.IDLE

	if target_state != current_state:
		current_state = target_state
		state_time = 0.0

	# Smoothing factor for lerp transitions
	var blend_speed: float = clamp(delta * 12.0, 0.05, 1.0)

	# Target transform variables
	var target_body_pitch: float = 0.0
	var target_torso_y: float = 0.95
	var target_head_pitch: float = 0.0

	var target_l_leg_x: float = 0.0
	var target_r_leg_x: float = 0.0
	var target_l_shin_x: float = 0.0
	var target_r_shin_x: float = 0.0

	var target_l_arm_x: float = 0.0
	var target_r_arm_x: float = 0.0
	var target_l_forearm_x: float = 0.0
	var target_r_forearm_x: float = 0.0

	var target_coat_tail_l_x: float = 8.0
	var target_coat_tail_r_x: float = 8.0

	match current_state:
		AnimState.JUMP:
			# --- JUMP (ASCENT) ANIMATION ---
			var jump_t = clamp(vel.y / 8.0, 0.0, 1.0)
			target_body_pitch = -8.0 - jump_t * 4.0
			target_torso_y = 0.98

			# Legs tuck upward for jump impulse
			target_l_leg_x = -30.0 - jump_t * 10.0
			target_l_shin_x = 45.0
			target_r_leg_x = 15.0 - jump_t * 10.0
			target_r_shin_x = 30.0

			# Arms extend dynamically for power and balance
			target_l_arm_x = 35.0
			target_r_arm_x = -35.0
			target_l_forearm_x = -20.0
			target_r_forearm_x = -20.0

			# Coat tails flare outward with upward push
			target_coat_tail_l_x = 40.0 + jump_t * 15.0
			target_coat_tail_r_x = 40.0 + jump_t * 15.0

		AnimState.FALL:
			# --- FALL (DESCENT) ANIMATION ---
			var fall_t = clamp(-vel.y / 12.0, 0.0, 1.0)
			target_body_pitch = -4.0
			target_torso_y = 0.93
			target_head_pitch = 4.0

			# Legs extend downward in preparation for landing
			target_l_leg_x = -10.0
			target_l_shin_x = 15.0
			target_r_leg_x = 5.0
			target_r_shin_x = 10.0

			# Arms spread out slightly for air control
			target_l_arm_x = 25.0
			target_r_arm_x = -25.0
			target_l_forearm_x = -25.0
			target_r_forearm_x = -25.0

			# Updraft causes coat tails to flutter high behind character
			target_coat_tail_l_x = 55.0 + fall_t * 20.0
			target_coat_tail_r_x = 55.0 + fall_t * 20.0

		AnimState.RUN, AnimState.WALK:
			# --- WALK & RUN ANIMATIONS ---
			var is_running = (current_state == AnimState.RUN)
			var stride_freq = 12.0 if is_running else 8.0
			var max_leg_deg = 42.0 if is_running else 26.0
			var max_arm_deg = 35.0 if is_running else 20.0
			var lean_deg = -12.0 if is_running else -4.0

			var cycle = sin(anim_time * stride_freq)
			var cycle_cos = cos(anim_time * stride_freq)

			target_body_pitch = lean_deg
			target_torso_y = 0.95 + abs(cycle_cos) * (0.05 if is_running else 0.02)
			target_head_pitch = -2.0 if is_running else 0.0

			# Leg strides
			target_l_leg_x = cycle * max_leg_deg
			target_r_leg_x = -cycle * max_leg_deg

			target_l_shin_x = max(0.0, -cycle * (35.0 if is_running else 25.0))
			target_r_shin_x = max(0.0, cycle * (35.0 if is_running else 25.0))

			# Opposite Arm swings
			target_l_arm_x = -cycle * max_arm_deg
			target_r_arm_x = cycle * max_arm_deg

			target_l_forearm_x = -15.0 if is_running else -5.0
			target_r_forearm_x = -15.0 if is_running else -5.0

			# Coat tails flowing behind in movement
			var tail_sway = (35.0 if is_running else 25.0) + abs(cycle) * (20.0 if is_running else 10.0)
			target_coat_tail_l_x = tail_sway
			target_coat_tail_r_x = tail_sway

		AnimState.IDLE:
			# --- IDLE ANIMATION ---
			var breath = sin(anim_time * 2.0)
			target_body_pitch = 0.0
			target_torso_y = 0.95 + breath * 0.015
			target_head_pitch = breath * 1.5

			target_l_leg_x = 0.0
			target_r_leg_x = 0.0
			target_l_shin_x = 0.0
			target_r_shin_x = 0.0

			target_l_arm_x = breath * 2.0
			target_r_arm_x = -breath * 2.0
			target_l_forearm_x = 0.0
			target_r_forearm_x = 0.0

			target_coat_tail_l_x = 8.0 + breath * 3.0
			target_coat_tail_r_x = 8.0 + breath * 3.0

	# --- APPLY SMOOTH LERP TRANSITIONS ---
	body_pivot.rotation_degrees.x = lerp(body_pivot.rotation_degrees.x, target_body_pitch, blend_speed)
	torso.position.y = lerp(torso.position.y, target_torso_y, blend_speed)
	head_pivot.rotation_degrees.x = lerp(head_pivot.rotation_degrees.x, target_head_pitch, blend_speed)

	left_leg_pivot.rotation_degrees.x = lerp(left_leg_pivot.rotation_degrees.x, target_l_leg_x, blend_speed)
	right_leg_pivot.rotation_degrees.x = lerp(right_leg_pivot.rotation_degrees.x, target_r_leg_x, blend_speed)
	left_shin_pivot.rotation_degrees.x = lerp(left_shin_pivot.rotation_degrees.x, target_l_shin_x, blend_speed)
	right_shin_pivot.rotation_degrees.x = lerp(right_shin_pivot.rotation_degrees.x, target_r_shin_x, blend_speed)

	left_arm_pivot.rotation_degrees.x = lerp(left_arm_pivot.rotation_degrees.x, target_l_arm_x, blend_speed)
	right_arm_pivot.rotation_degrees.x = lerp(right_arm_pivot.rotation_degrees.x, target_r_arm_x, blend_speed)
	left_forearm_pivot.rotation_degrees.x = lerp(left_forearm_pivot.rotation_degrees.x, target_l_forearm_x, blend_speed)
	right_forearm_pivot.rotation_degrees.x = lerp(right_forearm_pivot.rotation_degrees.x, target_r_forearm_x, blend_speed)

	coat_tail_left.rotation_degrees.x = lerp(coat_tail_left.rotation_degrees.x, target_coat_tail_l_x, blend_speed)
	coat_tail_right.rotation_degrees.x = lerp(coat_tail_right.rotation_degrees.x, target_coat_tail_r_x, blend_speed)
