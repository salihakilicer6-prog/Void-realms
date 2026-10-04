extends Node

signal connected_to_server()
signal connection_closed()
signal packet_received(type: String, payload: Dictionary)

var ws := WebSocketPeer.new()
var server_url := "ws://127.0.0.1:3000/game"
var auth_token := ""
var is_connected := false

func _ready() -> void:
	set_process(true)

func connect_to_server(url: String = "", token: String = "") -> void:
	if url != "":
		server_url = url
	if token != "":
		auth_token = token
	var err = ws.connect_to_url(server_url)
	if err != OK:
		print("[NetworkClient] Connection failed with error: ", err)
	else:
		print("[NetworkClient] Connecting to: ", server_url)

func _process(_delta: float) -> void:
	ws.poll()
	var state = ws.get_ready_state()
	if state == WebSocketPeer.STATE_OPEN:
		if not is_connected:
			is_connected = true
			emit_signal("connected_to_server")
			if auth_token != "":
				send_packet("AUTH", {"token": auth_token})
		while ws.get_available_packet_count() > 0:
			var pkt = ws.get_packet().get_string_from_utf8()
			var json = JSON.parse_string(pkt)
			if json and typeof(json) == TYPE_DICTIONARY:
				emit_signal("packet_received", json.get("type", ""), json.get("payload", {}))
	elif state == WebSocketPeer.STATE_CLOSED:
		if is_connected:
			is_connected = false
			emit_signal("connection_closed")

func send_packet(packet_type: String, payload: Dictionary) -> void:
	if ws.get_ready_state() == WebSocketPeer.STATE_OPEN:
		var data = {
			"type": packet_type,
			"seq": Time.get_ticks_msec(),
			"payload": payload,
			"timestamp": Time.get_unix_time_from_system()
		}
		ws.send_text(JSON.stringify(data))
