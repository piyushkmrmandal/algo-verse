-- =============================================================================
-- AlgoVerse :: Gamification Service — Badge Seed Data
-- Migration : V2__seed_badges.sql
-- =============================================================================

INSERT INTO badges (id, slug, name, description, icon_url, rarity, xp_reward, condition_type, condition_value)
VALUES
    (gen_random_uuid(), 'first-solve',     'First Blood',     'Solve your very first problem',              '/icons/badges/first-blood.svg',     'COMMON',    0,   'FIRST_SOLVE',        '{"threshold": 1}'),
    (gen_random_uuid(), 'easy-dozen',      'Easy Dozen',      'Solve 12 EASY difficulty problems',          '/icons/badges/easy-dozen.svg',      'COMMON',    50,  'PROBLEMS_SOLVED',    '{"threshold": 12, "difficulty": "EASY"}'),
    (gen_random_uuid(), 'medium-master',   'Medium Master',   'Solve 50 MEDIUM difficulty problems',        '/icons/badges/medium-master.svg',   'RARE',      200, 'PROBLEMS_SOLVED',    '{"threshold": 50, "difficulty": "MEDIUM"}'),
    (gen_random_uuid(), 'hard-crusher',    'Hard Crusher',    'Solve 10 HARD difficulty problems',          '/icons/badges/hard-crusher.svg',    'EPIC',      500, 'PROBLEMS_SOLVED',    '{"threshold": 10, "difficulty": "HARD"}'),
    (gen_random_uuid(), 'week-warrior',    'Week Warrior',    'Maintain a 7-day coding streak',             '/icons/badges/week-warrior.svg',    'RARE',      150, 'STREAK_DAYS',        '{"threshold": 7}'),
    (gen_random_uuid(), 'month-legend',    'Month Legend',    'Maintain a 30-day coding streak',            '/icons/badges/month-legend.svg',    'LEGENDARY', 500, 'STREAK_DAYS',        '{"threshold": 30}'),
    (gen_random_uuid(), 'century',         'Century',         'Solve 100 problems in total',                '/icons/badges/century.svg',         'EPIC',      300, 'TOTAL_SOLVES',       '{"threshold": 100}'),
    (gen_random_uuid(), 'speed-demon',     'Speed Demon',     'Solve a problem in under 5 minutes',         '/icons/badges/speed-demon.svg',     'RARE',      100, 'SPEED_SOLVE',        '{"maxExecutionTimeMs": 300000}')
ON CONFLICT (slug) DO NOTHING;
