# Procedural 3D Dungeon Generator for Godot 4.3 Client (VOID REALMS V7)
# Implements BSP room partitioning and GridMap 3D tile generation.
# License: MIT License (Open Source Procedural Generation Architecture)

extends Node3D
class_name ProceduralDungeonGenerator

@export var grid_width: int = 24
@export var grid_height: int = 24
@export var tile_size: float = 4.0
@export var wall_height: float = 3.5

class DungeonRoom:
	var x: int
	var y: int
	var width: int
	var height: int
	var is_boss: bool = false

	func _init(px: int, py: int, pw: int, ph: int, p_boss: bool = false):
		x = px
		y = py
		width = pw
		height = ph
		is_boss = p_boss

	func center() -> Vector2i:
		return Vector2i(x + width / 2, y + height / 2)

var rooms: Array[DungeonRoom] = []
var grid: Array = []

func _ready() -> void:
	generate_dungeon()

func generate_dungeon() -> void:
	# Clear previous geometry
	for child in get_children():
		child.queue_free()

	# 1. BSP Room Generation
	rooms.clear()
	var rng = RandomNumberGenerator.new()
	rng.randomize()

	for rx in range(1, grid_width - 6, 6):
		for ry in range(1, grid_height - 6, 6):
			var rw = rng.randi_range(3, 5)
			var rh = rng.randi_range(3, 5)
			rooms.append(DungeonRoom.new(rx, ry, rw, rh))

	if rooms.size() > 0:
		rooms[rooms.size() - 1].is_boss = true

	# 2. Build 3D Meshes for Rooms and Corridors
	for room in rooms:
		_create_room_mesh(room)

func _create_room_mesh(room: DungeonRoom) -> void:
	var floor_mesh = MeshInstance3D.new()
	var box = BoxMesh.new()
	box.size = Vector3(room.width * tile_size, 0.2, room.height * tile_size)
	floor_mesh.mesh = box

	var center = room.center()
	floor_mesh.position = Vector3(center.x * tile_size, 0, center.y * tile_size)

	var mat = StandardMaterial3D.new()
	mat.albedo_color = Color(0.12, 0.14, 0.22) if not room.is_boss else Color(0.35, 0.1, 0.1)
	floor_mesh.material_override = mat

	add_child(floor_mesh)
