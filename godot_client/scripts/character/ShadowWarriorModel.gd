# Shadow Warrior Protagonist 3D Character Model & Procedural Animation System (VOID REALMS)
# High-quality dark fantasy sci-fi anime protagonist (Shadow Warrior)
# Proportions: Athletic human (1.8m height), matte black long coat, dark armor, silver & deep-green accents.

extends Node3D
class_name ShadowWarriorModel

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
var mat_hair: StandardMaterial3D
var mat_skin: StandardMaterial3D

# Animation State Variables
var anim_time: float = 0.0
var parent_character: CharacterBody3D

func _ready() -> void:
	_create_materials()
	_build_character_mesh()
	parent_character = get_parent() as CharacterBody3D

func _process(delta: float) -> void:
	anim_time += delta
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

	# Matte pitch-black messy hair
	mat_hair = StandardMaterial3D.new()
	mat_hair.albedo_color = Color(0.02, 0.02, 0.03)
	mat_hair.roughness = 0.9

	# Pale skin tone for face silhouette
	mat_skin = StandardMaterial3D.new()
	mat_skin.albedo_color = Color(0.82, 0.72, 0.65)
	mat_skin.roughness = 0.6

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

	# --- HEAD & MESSY BLACK HAIR ---
	head_pivot = Node3D.new()
	head_pivot.name = "HeadPivot"
	head_pivot.position = Vector3(0, 0.44, 0)
	torso.add_child(head_pivot)

	# Sleek Face / Jaw Mesh
	var head_mesh = BoxMesh.new()
	head_mesh.size = Vector3(0.24, 0.28, 0.26)
	var head_inst = MeshInstance3D.new()
	head_inst.mesh = head_mesh
	head_inst.position = Vector3(0, 0.14, 0)
	head_inst.set_surface_override_material(0, mat_skin)
	head_pivot.add_child(head_inst)

	# Calm/Intimidating Eyes / Visor Accent
	var eyes_mesh = BoxMesh.new()
	eyes_mesh.size = Vector3(0.20, 0.04, 0.04)
	var eyes_inst = MeshInstance3D.new()
	eyes_inst.mesh = eyes_mesh
	eyes_inst.position = Vector3(0, 0.16, -0.12)
	eyes_inst.set_surface_override_material(0, mat_green_glow)
	head_pivot.add_child(eyes_inst)

	# Messy Black Hair Spikes (Layered strands)
	var hair_group = Node3D.new()
	hair_group.position = Vector3(0, 0.18, 0)
	head_pivot.add_child(hair_group)

	var spike_mesh = PrismMesh.new()
	spike_mesh.size = Vector3(0.12, 0.22, 0.14)

	# Top/Back/Side Spikes
	var spike_offsets = [
		Vector3(0, 0.12, 0.02),
		Vector3(-0.08, 0.10, -0.04),
		Vector3(0.08, 0.10, -0.04),
		Vector3(-0.10, 0.06, 0.06),
		Vector3(0.10, 0.06, 0.06),
		Vector3(0, 0.08, 0.10),
		Vector3(-0.06, 0.14, -0.08),
		Vector3(0.06, 0.14, -0.08)
	]
	var spike_rots = [
		Vector3(-15, 0, 0),
		Vector3(-20, 25, -15),
		Vector3(-20, -25, 15),
		Vector3(10, 35, -20),
		Vector3(10, -35, 20),
		Vector3(25, 0, 0),
		Vector3(-30, 15, -10),
		Vector3(-30, -15, 10)
	]

	for i in range(spike_offsets.size()):
		var sp = MeshInstance3D.new()
		sp.mesh = spike_mesh
		sp.position = spike_offsets[i]
		sp.rotation_degrees = spike_rots[i]
		sp.set_surface_override_material(0, mat_hair)
		hair_group.add_child(sp)

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

func _update_procedural_animations(_delta: float) -> void:
	if not parent_character:
		parent_character = get_parent() as CharacterBody3D
		if not parent_character:
			return

	var vel = parent_character.velocity
	var horiz_speed = Vector2(vel.x, vel.z).length()
	var is_floor = parent_character.is_on_floor()

	if not is_floor:
		# --- JUMP / AIRBORNE ANIMATION ---
		var jump_t = clamp(vel.y / 8.0, -1.0, 1.0)
		body_pivot.rotation_degrees.x = lerp(body_pivot.rotation_degrees.x, -10.0, 0.15)
		torso.position.y = lerp(torso.position.y, 0.98, 0.15)

		left_leg_pivot.rotation_degrees.x = lerp(left_leg_pivot.rotation_degrees.x, -25.0 - jump_t * 15.0, 0.2)
		left_shin_pivot.rotation_degrees.x = lerp(left_shin_pivot.rotation_degrees.x, 35.0, 0.2)

		right_leg_pivot.rotation_degrees.x = lerp(right_leg_pivot.rotation_degrees.x, 15.0 - jump_t * 10.0, 0.2)
		right_shin_pivot.rotation_degrees.x = lerp(right_shin_pivot.rotation_degrees.x, 20.0, 0.2)

		left_arm_pivot.rotation_degrees.x = lerp(left_arm_pivot.rotation_degrees.x, 30.0, 0.2)
		right_arm_pivot.rotation_degrees.x = lerp(right_arm_pivot.rotation_degrees.x, -30.0, 0.2)

		# Coat tails flare outward in wind
		coat_tail_left.rotation_degrees.x = lerp(coat_tail_left.rotation_degrees.x, 45.0, 0.2)
		coat_tail_right.rotation_degrees.x = lerp(coat_tail_right.rotation_degrees.x, 45.0, 0.2)

	elif horiz_speed > 0.1:
		# --- WALK & RUN ANIMATIONS ---
		var is_running = horiz_speed > 4.5
		var stride_freq = 12.0 if is_running else 8.0
		var max_leg_deg = 42.0 if is_running else 26.0
		var max_arm_deg = 35.0 if is_running else 20.0
		var lean_deg = -12.0 if is_running else -4.0

		var cycle = sin(anim_time * stride_freq)
		var cycle_cos = cos(anim_time * stride_freq)

		body_pivot.rotation_degrees.x = lerp(body_pivot.rotation_degrees.x, lean_deg, 0.15)
		torso.position.y = 0.95 + abs(cycle_cos) * (0.05 if is_running else 0.02)

		# Leg strides
		left_leg_pivot.rotation_degrees.x = cycle * max_leg_deg
		right_leg_pivot.rotation_degrees.x = -cycle * max_leg_deg

		left_shin_pivot.rotation_degrees.x = max(0.0, -cycle * 25.0)
		right_shin_pivot.rotation_degrees.x = max(0.0, cycle * 25.0)

		# Opposite Arm swings
		left_arm_pivot.rotation_degrees.x = -cycle * max_arm_deg
		right_arm_pivot.rotation_degrees.x = cycle * max_arm_deg

		left_forearm_pivot.rotation_degrees.x = -15.0 if is_running else -5.0
		right_forearm_pivot.rotation_degrees.x = -15.0 if is_running else -5.0

		# Coat tails flowing behind in movement
		var tail_sway = 25.0 + abs(cycle) * (20.0 if is_running else 10.0)
		coat_tail_left.rotation_degrees.x = tail_sway
		coat_tail_right.rotation_degrees.x = tail_sway

	else:
		# --- IDLE ANIMATION ---
		var breath = sin(anim_time * 2.0)
		body_pivot.rotation_degrees.x = lerp(body_pivot.rotation_degrees.x, 0.0, 0.1)
		torso.position.y = 0.95 + breath * 0.015
		head_pivot.rotation_degrees.x = breath * 1.5

		left_leg_pivot.rotation_degrees.x = lerp(left_leg_pivot.rotation_degrees.x, 0.0, 0.1)
		right_leg_pivot.rotation_degrees.x = lerp(right_leg_pivot.rotation_degrees.x, 0.0, 0.1)
		left_shin_pivot.rotation_degrees.x = lerp(left_shin_pivot.rotation_degrees.x, 0.0, 0.1)
		right_shin_pivot.rotation_degrees.x = lerp(right_shin_pivot.rotation_degrees.x, 0.0, 0.1)

		left_arm_pivot.rotation_degrees.x = lerp(left_arm_pivot.rotation_degrees.x, breath * 2.0, 0.1)
		right_arm_pivot.rotation_degrees.x = lerp(right_arm_pivot.rotation_degrees.x, -breath * 2.0, 0.1)

		# Gentle coat tail sway in breeze
		coat_tail_left.rotation_degrees.x = lerp(coat_tail_left.rotation_degrees.x, 8.0 + breath * 3.0, 0.1)
		coat_tail_right.rotation_degrees.x = lerp(coat_tail_right.rotation_degrees.x, 8.0 + breath * 3.0, 0.1)
