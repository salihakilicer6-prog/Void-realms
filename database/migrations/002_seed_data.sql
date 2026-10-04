-- ============================================================================
-- VOID REALMS - Database Schema Migration 002: Seed Data
-- ============================================================================

-- Seed Role Permissions
INSERT INTO admin_permissions (role, permission_node) VALUES
-- OWNER has full root access
('OWNER', 'admin.player.view'),
('OWNER', 'admin.player.kick'),
('OWNER', 'admin.player.ban'),
('OWNER', 'admin.player.unban'),
('OWNER', 'admin.player.mute'),
('OWNER', 'admin.player.teleport'),
('OWNER', 'admin.player.give_xp'),
('OWNER', 'admin.player.set_level'),
('OWNER', 'admin.player.give_gold'),
('OWNER', 'admin.player.give_item'),
('OWNER', 'admin.player.reset'),
('OWNER', 'admin.world.teleport'),
('OWNER', 'admin.world.spawn_npc'),
('OWNER', 'admin.world.spawn_enemy'),
('OWNER', 'admin.world.weather'),
('OWNER', 'admin.world.broadcast'),
('OWNER', 'admin.item.create'),
('OWNER', 'admin.item.grant'),
('OWNER', 'admin.server.maintenance'),
('OWNER', 'admin.server.metrics'),
('OWNER', 'admin.roles.manage'),
('OWNER', 'admin.audit.view'),

-- ADMIN
('ADMIN', 'admin.player.view'),
('ADMIN', 'admin.player.kick'),
('ADMIN', 'admin.player.ban'),
('ADMIN', 'admin.player.unban'),
('ADMIN', 'admin.player.mute'),
('ADMIN', 'admin.player.teleport'),
('ADMIN', 'admin.world.broadcast'),
('ADMIN', 'admin.server.metrics'),
('ADMIN', 'admin.audit.view'),

-- MODERATOR
('MODERATOR', 'admin.player.view'),
('MODERATOR', 'admin.player.kick'),
('MODERATOR', 'admin.player.mute'),
('MODERATOR', 'admin.world.broadcast')
ON CONFLICT DO NOTHING;

-- Seed Starter Quests
INSERT INTO quests (id, title, description, quest_type, required_level, objective_type, target_id, required_count, reward_xp, reward_gold) VALUES
('quest_001_awakening', 'Echoes in the Astral Mist', 'Defeat 5 Void Crawlers invading the outskirts of the Astral Sanctuary.', 'MAIN', 1, 'KILL_MONSTER', 'void_crawler', 5, 250, 150),
('quest_002_rift_scout', 'Stabilize the Phase Rift', 'Travel to the Void Wastes and purge 3 Rift Stalkers.', 'MAIN', 2, 'KILL_MONSTER', 'rift_stalker', 3, 500, 300),
('quest_003_abyssal_threat', 'Wrath of the Nether Lord', 'Slay the Abyssal Lord in the deep cavern to earn legendary glory.', 'MAIN', 5, 'KILL_MONSTER', 'abyssal_lord', 1, 2000, 1500),
('quest_daily_void_hunt', 'Daily Bounty: Void Remnants', 'Purge 10 Void entities across any frontier zone.', 'DAILY', 1, 'KILL_MONSTER', 'any_void', 10, 800, 500)
ON CONFLICT (id) DO NOTHING;

-- Seed Base Items
INSERT INTO items (id, item_code, name, item_type, equip_slot, rarity, required_level, damage, defense, crit_bonus, str_bonus, agi_bonus, element, special_effect, is_stackable, base_value) VALUES
('11111111-1111-1111-1111-111111111101', 'novice_void_blade', 'Novice Astral Blade', 'WEAPON', 'MAIN_WEAPON', 'COMMON', 1, 15, 0, 2.0, 3, 2, 'PHYSICAL', 'A balanced blade imbued with faint cosmic starlight.', FALSE, 50),
('11111111-1111-1111-1111-111111111102', 'astral_apprentice_tunic', 'Apprentice Astral Tunic', 'ARMOR', 'CHEST', 'COMMON', 1, 0, 12, 0.0, 1, 1, 'PHYSICAL', 'Woven from reinforced void-silk.', FALSE, 40),
('11111111-1111-1111-1111-111111111103', 'minor_health_potion', 'Astral Draught', 'CONSUMABLE', NULL, 'COMMON', 1, 0, 0, 0.0, 0, 0, 'PHYSICAL', 'Instantly restores 75 Health.', TRUE, 15),
('11111111-1111-1111-1111-111111111104', 'minor_mana_potion', 'Ether Flask', 'CONSUMABLE', NULL, 'COMMON', 1, 0, 0, 0.0, 0, 0, 'PHYSICAL', 'Restores 60 Energy/Mana.', TRUE, 20),
('11111111-1111-1111-1111-111111111105', 'reaper_scythe_owner', 'Void Reaver: Crown of Infinity', 'WEAPON', 'MAIN_WEAPON', 'OWNER', 1, 9999, 500, 50.0, 500, 500, 'VOID', 'The sovereign weapon forged by the Architects of the Void. Pierces all realities.', FALSE, 1000000)
ON CONFLICT (id) DO NOTHING;
