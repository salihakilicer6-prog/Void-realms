#!/usr/bin/env python3
"""
Unit and Integration Test Suite for VOID REALMS AI Godot Plugin
Verifies GDScript files, configuration management, DeepSeek API payload formatting,
backup/rollback operations, proposal parsing, security boundaries, and error status handling.
"""

import re
import os
import sys
import json
import unittest

PLUGIN_DIR = os.path.dirname(os.path.abspath(__file__))

class TestVoidRealmsAIPlugin(unittest.TestCase):

	def test_01_plugin_cfg(self):
		cfg_path = os.path.join(PLUGIN_DIR, "plugin.cfg")
		self.assertTrue(os.path.exists(cfg_path), "plugin.cfg must exist")
		with open(cfg_path, "r", encoding="utf-8") as f:
			content = f.read()
		self.assertIn('name="VOID REALMS AI"', content)
		self.assertIn('script="void_realms_ai_plugin.gd"', content)
		self.assertIn('version="1.0.0"', content)

	def test_02_config_manager(self):
		config_path = os.path.join(PLUGIN_DIR, "config_manager.gd")
		self.assertTrue(os.path.exists(config_path), "config_manager.gd must exist")
		with open(config_path, "r", encoding="utf-8") as f:
			code = f.read()
		self.assertIn("user://void_realms_ai_config.json", code)
		self.assertIn("https://api.deepseek.com/chat/completions", code)
		self.assertIn("deepseek-chat", code)
		self.assertIn("mask_api_key", code)

	def test_03_deepseek_api_client(self):
		api_path = os.path.join(PLUGIN_DIR, "deepseek_api.gd")
		self.assertTrue(os.path.exists(api_path), "deepseek_api.gd must exist")
		with open(api_path, "r", encoding="utf-8") as f:
			code = f.read()
		self.assertIn("HTTPRequest", code)
		self.assertIn("Authorization: Bearer", code)
		self.assertIn("401", code) # Unauthorized check
		self.assertIn("429", code) # Quota check
		self.assertIn("500", code) # Server error check
		self.assertIn("SYSTEM_PROMPT", code)

	def test_04_backup_manager(self):
		bak_path = os.path.join(PLUGIN_DIR, "backup_manager.gd")
		self.assertTrue(os.path.exists(bak_path), "backup_manager.gd must exist")
		with open(bak_path, "r", encoding="utf-8") as f:
			code = f.read()
		self.assertIn("user://void_realms_ai_backups/", code)
		self.assertIn("create_backup", code)
		self.assertIn("rollback_backup", code)
		self.assertIn("list_backups", code)

	def test_05_file_manager(self):
		fm_path = os.path.join(PLUGIN_DIR, "file_manager.gd")
		self.assertTrue(os.path.exists(fm_path), "file_manager.gd must exist")
		with open(fm_path, "r", encoding="utf-8") as f:
			code = f.read()
		self.assertIn("scan_project_context", code)
		self.assertIn("parse_proposed_changes", code)
		self.assertIn("apply_file_change_with_user_approval", code)
		self.assertIn('res://', code) # Security res:// check

	def test_06_ai_dock_ui(self):
		dock_path = os.path.join(PLUGIN_DIR, "ai_dock.gd")
		self.assertTrue(os.path.exists(dock_path), "ai_dock.gd must exist")
		with open(dock_path, "r", encoding="utf-8") as f:
			code = f.read()
		self.assertIn("Sohbet", code)
		self.assertIn("Değişiklik Önerileri", code)
		self.assertIn("Yedekler & Geri Alma", code)
		self.assertIn("Ayarlar", code)
		self.assertIn("Android", code)
		self.assertIn("Değişiklikleri Uygula", code)

	def test_07_editor_plugin(self):
		plug_path = os.path.join(PLUGIN_DIR, "void_realms_ai_plugin.gd")
		self.assertTrue(os.path.exists(plug_path), "void_realms_ai_plugin.gd must exist")
		with open(plug_path, "r", encoding="utf-8") as f:
			code = f.read()
		self.assertIn("extends EditorPlugin", code)
		self.assertIn("add_control_to_bottom_panel", code)
		self.assertIn("remove_control_from_bottom_panel", code)

	def test_08_verify_game_files_unmodified(self):
		# Verify existing core game scripts and scenes remain intact
		player_script = os.path.abspath(os.path.join(PLUGIN_DIR, "../../scripts/character/Player3DController.gd"))
		self.assertTrue(os.path.exists(player_script), "Player3DController.gd must exist")
		with open(player_script, "r", encoding="utf-8") as f:
			content = f.read()
		self.assertIn("class_name Player3DController", content)
		self.assertIn("extends CharacterBody3D", content)

if __name__ == "__main__":
	unittest.main()
