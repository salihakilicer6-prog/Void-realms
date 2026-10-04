extends Control

@onready var search_input = $VBox/SearchRow/PlayerSearchInput
@onready var action_status = $VBox/StatusLabel
@onready var audit_list = $VBox/AuditLogs/Scroll/LogContainer

var current_role := "PLAYER"

func _ready() -> void:
	visible = false
	NetworkClient.connect("packet_received", Callable(self, "_on_packet_received"))

func open_panel(user_role: String) -> void:
	current_role = user_role
	if current_role == "OWNER" or current_role == "ADMIN":
		visible = true
	else:
		visible = false
		print("[Security] Client-side attempt to open Admin panel denied.")

func _on_grant_xp_pressed(amount: int) -> void:
	NetworkClient.send_packet("ADMIN_ACTION", {
		"action": "GIVE_XP",
		"amount": amount
	})

func _on_grant_item_pressed(rarity: String) -> void:
	NetworkClient.send_packet("ADMIN_ACTION", {
		"action": "GIVE_ITEM",
		"rarity": rarity
	})

func _on_broadcast_pressed(msg: String) -> void:
	NetworkClient.send_packet("ADMIN_ACTION", {
		"action": "BROADCAST",
		"message": msg
	})

func _on_packet_received(type: String, payload: Dictionary) -> void:
	if type == "ADMIN_RESPONSE":
		action_status.text = payload.get("message", "Action processed.")
	elif type == "SYSTEM_ANNOUNCEMENT":
		print("[SERVER ANNOUNCEMENT]: ", payload.get("message", ""))
