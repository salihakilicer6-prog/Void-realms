-- ============================================================================
-- VOID REALMS - Database Schema Migration 001
-- PostgreSQL authoritative schema for users, characters, inventory,
-- equipment, quests, combat, economy, admin roles, and audit logging.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Role Enum
DO $$ BEGIN
    CREATE TYPE user_role AS ENUM ('PLAYER', 'MODERATOR', 'ADMIN', 'OWNER');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Item Rarity Enum
DO $$ BEGIN
    CREATE TYPE item_rarity AS ENUM (
        'COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGENDARY',
        'MYTHIC', 'DIVINE', 'ANCIENT', 'OWNER'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Equipment Slot Enum
DO $$ BEGIN
    CREATE TYPE equip_slot AS ENUM (
        'MAIN_WEAPON', 'OFF_HAND', 'HELMET', 'CHEST',
        'GLOVES', 'BOOTS', 'RING_1', 'RING_2', 'NECKLACE', 'SPECIAL'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 1. Users Table (Authentication & Account Security)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(32) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(64) NOT NULL,
    role user_role NOT NULL DEFAULT 'PLAYER',
    is_banned BOOLEAN NOT NULL DEFAULT FALSE,
    ban_reason TEXT,
    ban_expires_at TIMESTAMP WITH TIME ZONE,
    is_muted BOOLEAN NOT NULL DEFAULT FALSE,
    mute_expires_at TIMESTAMP WITH TIME ZONE,
    last_login TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Characters Table (Player progression & state)
CREATE TABLE IF NOT EXISTS characters (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(32) UNIQUE NOT NULL,
    level INTEGER NOT NULL DEFAULT 1 CHECK (level >= 1 AND level <= 100),
    xp BIGINT NOT NULL DEFAULT 0 CHECK (xp >= 0),
    gold BIGINT NOT NULL DEFAULT 100 CHECK (gold >= 0),
    void_shards BIGINT NOT NULL DEFAULT 10 CHECK (void_shards >= 0),

    -- Current Vitals
    hp INTEGER NOT NULL DEFAULT 150 CHECK (hp >= 0),
    max_hp INTEGER NOT NULL DEFAULT 150 CHECK (max_hp > 0),
    mana INTEGER NOT NULL DEFAULT 100 CHECK (mana >= 0),
    max_mana INTEGER NOT NULL DEFAULT 100 CHECK (max_mana > 0),

    -- Attributes
    strength INTEGER NOT NULL DEFAULT 10 CHECK (strength >= 0),
    defense INTEGER NOT NULL DEFAULT 8 CHECK (defense >= 0),
    agility INTEGER NOT NULL DEFAULT 10 CHECK (agility >= 0),
    crit_chance NUMERIC(5,2) NOT NULL DEFAULT 5.00 CHECK (crit_chance >= 0.0 AND crit_chance <= 100.0),
    crit_damage NUMERIC(5,2) NOT NULL DEFAULT 150.00 CHECK (crit_damage >= 100.0),

    -- World position
    zone_id VARCHAR(64) NOT NULL DEFAULT 'astral_sanctuary',
    pos_x NUMERIC(10,2) NOT NULL DEFAULT 0.0,
    pos_y NUMERIC(10,2) NOT NULL DEFAULT 0.0,
    pos_z NUMERIC(10,2) NOT NULL DEFAULT 0.0,

    -- Status
    is_dead BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Items Table (Procedural & Base Items)
CREATE TABLE IF NOT EXISTS items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    item_code VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    item_type VARCHAR(32) NOT NULL, -- WEAPON, ARMOR, CONSUMABLE, MATERIAL, ARTIFACT
    equip_slot equip_slot,
    rarity item_rarity NOT NULL DEFAULT 'COMMON',
    required_level INTEGER NOT NULL DEFAULT 1,

    -- Combat & Stat Affixes
    damage INTEGER NOT NULL DEFAULT 0,
    defense INTEGER NOT NULL DEFAULT 0,
    crit_bonus NUMERIC(5,2) NOT NULL DEFAULT 0.0,
    str_bonus INTEGER NOT NULL DEFAULT 0,
    agi_bonus INTEGER NOT NULL DEFAULT 0,
    element VARCHAR(32) DEFAULT 'PHYSICAL', -- PHYSICAL, VOID, ASTRAL, FIRE, ICE, LIGHTNING

    -- Lore & Metadata
    special_effect TEXT,
    is_stackable BOOLEAN NOT NULL DEFAULT FALSE,
    max_stack INTEGER NOT NULL DEFAULT 1,
    base_value BIGINT NOT NULL DEFAULT 10,
    seed_hash VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Inventory Table
CREATE TABLE IF NOT EXISTS inventory (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    item_id UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    slot_index INTEGER NOT NULL CHECK (slot_index >= 0 AND slot_index < 40),
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_character_slot UNIQUE (character_id, slot_index)
);

-- 5. Equipment Table
CREATE TABLE IF NOT EXISTS equipment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    slot equip_slot NOT NULL,
    item_id UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    equipped_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_character_equip_slot UNIQUE (character_id, slot)
);

-- 6. Quests Definition & Progress
CREATE TABLE IF NOT EXISTS quests (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    quest_type VARCHAR(32) NOT NULL DEFAULT 'MAIN', -- MAIN, SIDE, DAILY
    required_level INTEGER NOT NULL DEFAULT 1,
    objective_type VARCHAR(32) NOT NULL, -- KILL_MONSTER, COLLECT_ITEM, TALK_NPC, EXPLORE
    target_id VARCHAR(64) NOT NULL,
    required_count INTEGER NOT NULL DEFAULT 1,
    reward_xp BIGINT NOT NULL DEFAULT 0,
    reward_gold BIGINT NOT NULL DEFAULT 0,
    reward_item_id UUID REFERENCES items(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS quest_progress (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    quest_id VARCHAR(64) NOT NULL REFERENCES quests(id) ON DELETE CASCADE,
    current_count INTEGER NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    is_claimed BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT unique_character_quest UNIQUE (character_id, quest_id)
);

-- 7. Economy & Transactions Audit
CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    transaction_type VARCHAR(32) NOT NULL, -- SHOP_BUY, SHOP_SELL, TRADE, ADMIN_GRANT, REWARD
    currency VARCHAR(16) NOT NULL, -- GOLD, VOID_SHARD
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    reference_id TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 8. Player-to-Player Trading
CREATE TABLE IF NOT EXISTS trades (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sender_id UUID NOT NULL REFERENCES characters(id),
    receiver_id UUID NOT NULL REFERENCES characters(id),
    sender_gold BIGINT NOT NULL DEFAULT 0,
    receiver_gold BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, ACCEPTED, REJECTED, CANCELLED, COMPLETED
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

-- 9. Admin Roles & Permissions
CREATE TABLE IF NOT EXISTS admin_permissions (
    id SERIAL PRIMARY KEY,
    role user_role NOT NULL,
    permission_node VARCHAR(64) NOT NULL,
    CONSTRAINT unique_role_permission UNIQUE (role, permission_node)
);

-- 10. Admin Audit Logs (Tamper-evident record of all privileged actions)
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    admin_user_id UUID NOT NULL REFERENCES users(id),
    admin_username VARCHAR(32) NOT NULL,
    admin_role user_role NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_identifier VARCHAR(128),
    parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    result VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 11. Server Events & World State
CREATE TABLE IF NOT EXISTS server_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_type VARCHAR(64) NOT NULL,
    zone_id VARCHAR(64),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ends_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_characters_user_id ON characters(user_id);
CREATE INDEX IF NOT EXISTS idx_inventory_char_id ON inventory(character_id);
CREATE INDEX IF NOT EXISTS idx_equipment_char_id ON equipment(character_id);
CREATE INDEX IF NOT EXISTS idx_quest_progress_char ON quest_progress(character_id);
CREATE INDEX IF NOT EXISTS idx_audit_admin ON admin_audit_logs(admin_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON admin_audit_logs(created_at DESC);
