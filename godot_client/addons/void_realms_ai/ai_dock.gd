@tool
extends Control
class_name VoidRealmsAIDock

var api_client: DeepSeekAPIClient
var current_proposals: Array = []

# UI References
var tab_container: TabContainer

# Chat Tab UI
var chat_log: RichTextLabel
var chat_input: TextEdit
var send_button: Button
var include_context_checkbox: CheckBox
var clear_chat_button: Button
var status_label: Label

# Proposals Tab UI
var proposals_vbox: VBoxContainer
var proposals_status_label: Label

# Backups Tab UI
var backups_vbox: VBoxContainer

# Settings Tab UI
var endpoint_input: LineEdit
var model_input: LineEdit
var api_key_input: LineEdit
var save_settings_button: Button
var settings_status_label: Label

# Android / Platform Banner UI
var platform_info_label: Label

func _init() -> void:
	custom_minimum_size = Vector2(400, 300)
	size_flags_horizontal = Control.SIZE_EXPAND_FILL
	size_flags_vertical = Control.SIZE_EXPAND_FILL

func _ready() -> void:
	api_client = DeepSeekAPIClient.new()
	add_child(api_client)
	api_client.request_started.connect(_on_api_request_started)
	api_client.request_completed.connect(_on_api_request_completed)

	_build_ui()
	_load_settings_into_ui()
	_refresh_backups_list()
	_update_platform_info()

func _build_ui() -> void:
	# Main Container
	var main_vbox := VBoxContainer.new()
	main_vbox.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT, Control.PRESET_MODE_MINSIZE, 6)
	add_child(main_vbox)

	# Header
	var header_hbox := HBoxContainer.new()
	var title_label := Label.new()
	title_label.text = "🤖 VOID REALMS AI Assistant (Godot 4.7.2)"
	title_label.add_theme_font_size_override("font_size", 16)
	header_hbox.add_child(title_label)
	main_vbox.add_child(header_hbox)

	# Tab Container
	tab_container = TabContainer.new()
	tab_container.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tab_container.size_flags_vertical = Control.SIZE_EXPAND_FILL
	main_vbox.add_child(tab_container)

	# ---------------- TAB 1: CHAT ----------------
	var chat_tab := VBoxContainer.new()
	chat_tab.name = "Sohbet"
	tab_container.add_child(chat_tab)

	chat_log = RichTextLabel.new()
	chat_log.bbcode_enabled = true
	chat_log.scroll_following = true
	chat_log.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	chat_log.size_flags_vertical = Control.SIZE_EXPAND_FILL
	chat_log.text = "[color=#8888ff][b]VOID REALMS AI Chat Paneline Hoş Geldiniz![/b][/color]\nGodot GDScript, sahne yapısı veya oyun mimarisi hakkında soru sorabilir, kod düzeltmeleri ve geliştirmeleri isteyebilirsiniz.\n"
	chat_tab.add_child(chat_log)

	status_label = Label.new()
	status_label.text = "Durum: Hazır"
	status_label.add_theme_color_override("font_color", Color(0.7, 0.7, 0.7))
	chat_tab.add_child(status_label)

	var input_hbox := HBoxContainer.new()
	chat_input = TextEdit.new()
	chat_input.placeholder_text = "İsteğinizi buraya yazın (Örn: Player3DController.gd dosyasındaki zıplama mekaniğini geliştir)..."
	chat_input.custom_minimum_size = Vector2(0, 60)
	chat_input.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	input_hbox.add_child(chat_input)

	send_button = Button.new()
	send_button.text = "Gönder 🚀"
	send_button.custom_minimum_size = Vector2(100, 60)
	send_button.pressed.connect(_on_send_pressed)
	input_hbox.add_child(send_button)
	chat_tab.add_child(input_hbox)

	var options_hbox := HBoxContainer.new()
	include_context_checkbox = CheckBox.new()
	include_context_checkbox.text = "Proje Kodlarını ve Sahneleri Ek İçerik (Context) Olarak Gönder"
	include_context_checkbox.button_pressed = true
	options_hbox.add_child(include_context_checkbox)

	clear_chat_button = Button.new()
	clear_chat_button.text = "Sohbeti Temizle"
	clear_chat_button.pressed.connect(_on_clear_chat_pressed)
	options_hbox.add_child(clear_chat_button)
	chat_tab.add_child(options_hbox)

	# ---------------- TAB 2: PROPOSALS ----------------
	var proposals_tab := VBoxContainer.new()
	proposals_tab.name = "Değişiklik Önerileri"
	tab_container.add_child(proposals_tab)

	proposals_status_label = Label.new()
	proposals_status_label.text = "AI yanıtından tespit edilen kod/dosya değişiklikleri burada listelenir. Değişiklikler onayınız olmadan uygulanmaz."
	proposals_status_label.autowrap_mode = TextServer.AUTOWRAP_WORD
	proposals_tab.add_child(proposals_status_label)

	var scroll_prop := ScrollContainer.new()
	scroll_prop.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll_prop.size_flags_vertical = Control.SIZE_EXPAND_FILL
	proposals_tab.add_child(scroll_prop)

	proposals_vbox = VBoxContainer.new()
	proposals_vbox.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll_prop.add_child(proposals_vbox)

	# ---------------- TAB 3: BACKUPS & ROLLBACK ----------------
	var backups_tab := VBoxContainer.new()
	backups_tab.name = "Yedekler & Geri Alma"
	tab_container.add_child(backups_tab)

	var backups_info := Label.new()
	backups_info.text = "Değiştirilen tüm dosyaların otomatik yedekleri 'user://void_realms_ai_backups/' klasöründe saklanır. İstediğiniz an geri alabilirsiniz."
	backups_info.autowrap_mode = TextServer.AUTOWRAP_WORD
	backups_tab.add_child(backups_info)

	var refresh_backups_btn := Button.new()
	refresh_backups_btn.text = "Yedek Listesini Yenile 🔄"
	refresh_backups_btn.pressed.connect(_refresh_backups_list)
	backups_tab.add_child(refresh_backups_btn)

	var scroll_bak := ScrollContainer.new()
	scroll_bak.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll_bak.size_flags_vertical = Control.SIZE_EXPAND_FILL
	backups_tab.add_child(scroll_bak)

	backups_vbox = VBoxContainer.new()
	backups_vbox.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll_bak.add_child(backups_vbox)

	# ---------------- TAB 4: SETTINGS ----------------
	var settings_tab := VBoxContainer.new()
	settings_tab.name = "Ayarlar"
	tab_container.add_child(settings_tab)

	var sec_label := Label.new()
	sec_label.text = "🔒 Güvenlik Notu: API anahtarınız proje kodlarına veya GitHub'a yazılmaz. Güvenli yerel 'user://' alanında saklanır."
	sec_label.add_theme_color_override("font_color", Color(0.4, 0.9, 0.5))
	sec_label.autowrap_mode = TextServer.AUTOWRAP_WORD
	settings_tab.add_child(sec_label)

	# Endpoint
	var ep_label := Label.new()
	ep_label.text = "API Endpoint URL:"
	settings_tab.add_child(ep_label)
	endpoint_input = LineEdit.new()
	endpoint_input.placeholder_text = VoidRealmsAIConfigManager.DEFAULT_ENDPOINT
	settings_tab.add_child(endpoint_input)

	# Model
	var model_label := Label.new()
	model_label.text = "Model Adı:"
	settings_tab.add_child(model_label)
	model_input = LineEdit.new()
	model_input.placeholder_text = VoidRealmsAIConfigManager.DEFAULT_MODEL
	settings_tab.add_child(model_input)

	# API Key
	var key_label := Label.new()
	key_label.text = "DeepSeek API Key:"
	settings_tab.add_child(key_label)
	api_key_input = LineEdit.new()
	api_key_input.secret = true
	api_key_input.placeholder_text = "sk-..."
	settings_tab.add_child(api_key_input)

	save_settings_button = Button.new()
	save_settings_button.text = "Ayarları Kaydet 💾"
	save_settings_button.pressed.connect(_on_save_settings_pressed)
	settings_tab.add_child(save_settings_button)

	settings_status_label = Label.new()
	settings_status_label.text = ""
	settings_tab.add_child(settings_status_label)

	# ---------------- TAB 5: PLATFORM / ANDROID ----------------
	var platform_tab := VBoxContainer.new()
	platform_tab.name = "Platform Bilgisi"
	tab_container.add_child(platform_tab)

	platform_info_label = Label.new()
	platform_info_label.autowrap_mode = TextServer.AUTOWRAP_WORD
	platform_tab.add_child(platform_info_label)

func _update_platform_info() -> void:
	var os_name := OS.get_name()
	var is_editor := Engine.is_editor_hint()

	var text := "Çalışma Zamanı Platformu: " + os_name + "\n"
	text += "Editör Modu (is_editor_hint): " + str(is_editor) + "\n\n"

	if os_name == "Android":
		text += "⚠️ ANDROİD PLATFORM UYARISI:\n"
		text += "Godot EditorPlugin ve EditorInterface eklenti mimarisi Masaüstü Godot Editörü için tasarlanmıştır. "
		text += "Android Godot 4.7.2 mobil çalışma zamanında Editor Dock arayüzü ve editör dosya sistem paneli desteklenmez veya kısıtlıdır.\n"
		text += "Bu eklenti masaüstü ortamında tam performansla çalışır."
	else:
		text += "✅ Masaüstü Godot Editörü algılandı. VOID REALMS AI eklentisi tüm editör panelleri ve yedekleme mekanizmalarıyla aktif durumdadır."

	if platform_info_label:
		platform_info_label.text = text

func _load_settings_into_ui() -> void:
	var cfg := VoidRealmsAIConfigManager.load_config()
	endpoint_input.text = cfg.get("endpoint", VoidRealmsAIConfigManager.DEFAULT_ENDPOINT)
	model_input.text = cfg.get("model", VoidRealmsAIConfigManager.DEFAULT_MODEL)
	api_key_input.text = cfg.get("api_key", "")

func _on_save_settings_pressed() -> void:
	var ep := endpoint_input.text
	var md := model_input.text
	var key := api_key_input.text

	var ok := VoidRealmsAIConfigManager.save_config(ep, md, key)
	if ok:
		settings_status_label.text = "✅ Ayarlar başarıyla yerel 'user://' yapılandırma dosyasına kaydedildi."
		settings_status_label.add_theme_color_override("font_color", Color(0.2, 0.9, 0.3))
	else:
		settings_status_label.text = "❌ Ayarlar kaydedilirken bir hata oluştu."
		settings_status_label.add_theme_color_override("font_color", Color(0.9, 0.2, 0.2))

func _on_send_pressed() -> void:
	var prompt := chat_input.text.strip_edges()
	if prompt.is_empty():
		return

	chat_log.append_text("\n[color=#ffff88][b]Kullanıcı:[/b][/color] " + prompt + "\n")
	chat_input.text = ""

	var context := ""
	if include_context_checkbox.button_pressed:
		context = VoidRealmsAIFileManager.scan_project_context()

	api_client.send_chat_prompt(prompt, context)

func _on_clear_chat_pressed() -> void:
	chat_log.text = "[color=#8888ff][b]VOID REALMS AI Chat Paneline Hoş Geldiniz![/b][/color]\n"
	status_label.text = "Durum: Sohbet temizlendi."

func _on_api_request_started() -> void:
	send_button.disabled = true
	status_label.text = "Durum: DeepSeek sunucusuna istek gönderiliyor..."
	status_label.add_theme_color_override("font_color", Color(0.9, 0.8, 0.2))

func _on_api_request_completed(success: bool, content: String, error_message: String) -> void:
	send_button.disabled = false
	if success:
		status_label.text = "Durum: Yanıt başarıyla alındı."
		status_label.add_theme_color_override("font_color", Color(0.2, 0.9, 0.3))
		chat_log.append_text("\n[color=#88ff88][b]VOID REALMS AI:[/b][/color]\n" + content + "\n")

		# Parse any proposals
		var proposals := VoidRealmsAIFileManager.parse_proposed_changes(content)
		if not proposals.is_empty():
			_display_proposals(proposals)
			tab_container.current_tab = 1 # Switch to proposals tab to show confirmation UI
	else:
		status_label.text = "Durum: Hata oluştu."
		status_label.add_theme_color_override("font_color", Color(0.9, 0.2, 0.2))
		chat_log.append_text("\n[color=#ff6666][b]Hata:[/b] " + error_message + "[/color]\n")

func _display_proposals(proposals: Array) -> void:
	current_proposals = proposals
	for child in proposals_vbox.get_children():
		child.queue_free()

	if proposals.is_empty():
		proposals_status_label.text = "Aktif değişiklik önerisi yok."
		return

	proposals_status_label.text = "⚠️ " + str(proposals.size()) + " adet dosya değişikliği önerildi. Uygulamadan önce inceleyip onaylayın:"

	for idx in range(proposals.size()):
		var prop: Dictionary = proposals[idx]
		var filepath: String = prop.get("filepath", "res://bilinmeyen_dosya.gd")
		var new_code: String = prop.get("new_content", "")

		var panel := PanelContainer.new()
		var vbox := VBoxContainer.new()
		panel.add_child(vbox)

		var file_lbl := Label.new()
		file_lbl.text = "📄 Hedef Dosya: " + filepath
		file_lbl.add_theme_font_size_override("font_size", 14)
		vbox.add_child(file_lbl)

		var code_prev := TextEdit.new()
		code_prev.editable = false
		code_prev.custom_minimum_size = Vector2(0, 120)
		code_prev.text = new_code
		vbox.add_child(code_prev)

		var btn_hbox := HBoxContainer.new()

		var apply_btn := Button.new()
		apply_btn.text = "✅ Değişiklikleri Uygula (Onayla)"
		apply_btn.pressed.connect(func(): _apply_proposal(idx))
		btn_hbox.add_child(apply_btn)

		var reject_btn := Button.new()
		reject_btn.text = "❌ İptal Et"
		reject_btn.pressed.connect(func(): _reject_proposal(idx))
		btn_hbox.add_child(reject_btn)

		vbox.add_child(btn_hbox)
		proposals_vbox.add_child(panel)

func _apply_proposal(idx: int) -> void:
	if idx < 0 or idx >= current_proposals.size():
		return

	var prop: Dictionary = current_proposals[idx]
	var filepath: String = prop.get("filepath", "")
	var new_code: String = prop.get("new_content", "")

	var res := VoidRealmsAIFileManager.apply_file_change_with_user_approval(filepath, new_code)
	if res.get("success", false):
		chat_log.append_text("\n[color=#88ff88]✅ Değişiklik başarıyla uygulandı: " + filepath + " (Yedek ID: " + str(res.get("backup_id", "")) + ")[/color]\n")
		current_proposals.remove_at(idx)
		_display_proposals(current_proposals)
		_refresh_backups_list()
	else:
		chat_log.append_text("\n[color=#ff6666]❌ Değişiklik uygulanamadı: " + str(res.get("error", "")) + "[/color]\n")

func _reject_proposal(idx: int) -> void:
	if idx >= 0 and idx < current_proposals.size():
		current_proposals.remove_at(idx)
		_display_proposals(current_proposals)

func _refresh_backups_list() -> void:
	for child in backups_vbox.get_children():
		child.queue_free()

	var backups := VoidRealmsAIBackupManager.list_backups()
	if backups.is_empty():
		var empty_lbl := Label.new()
		empty_lbl.text = "Henüz yedeklenmiş dosya bulunmuyor."
		backups_vbox.add_child(empty_lbl)
		return

	for entry in backups:
		if not (entry is Dictionary):
			continue
		var bak_id: String = entry.get("id", "")
		var target_path: String = entry.get("target_path", "")
		var timestamp: String = entry.get("timestamp", "")

		var hbox := HBoxContainer.new()
		var lbl := Label.new()
		lbl.text = "🕒 [" + timestamp + "] " + target_path
		lbl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		hbox.add_child(lbl)

		var rollback_btn := Button.new()
		rollback_btn.text = "🔄 Geri Al (Rollback)"
		rollback_btn.pressed.connect(func(): _rollback_backup_entry(bak_id))
		hbox.add_child(rollback_btn)

		backups_vbox.add_child(hbox)

func _rollback_backup_entry(bak_id: String) -> void:
	var res := VoidRealmsAIBackupManager.rollback_backup(bak_id)
	if res.get("success", false):
		chat_log.append_text("\n[color=#88ff88]🔄 Dosya yedekten geri yüklendi: " + str(res.get("target_path", "")) + "[/color]\n")
		status_label.text = "Durum: Dosya geri yüklendi."
	else:
		chat_log.append_text("\n[color=#ff6666]❌ Geri yükleme hatası: " + str(res.get("error", "")) + "[/color]\n")
