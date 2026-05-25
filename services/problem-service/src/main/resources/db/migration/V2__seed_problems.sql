-- =============================================================================
-- AlgoVerse :: Problem Service — Seed Data
-- Migration : V2__seed_problems.sql
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Topics seed
-- ---------------------------------------------------------------------------
INSERT INTO topics (slug, name) VALUES
  ('array',               'Array'),
  ('hash-table',          'Hash Table'),
  ('dynamic-programming', 'Dynamic Programming'),
  ('binary-search',       'Binary Search'),
  ('tree',                'Tree'),
  ('graph',               'Graph'),
  ('two-pointers',        'Two Pointers'),
  ('sliding-window',      'Sliding Window'),
  ('backtracking',        'Backtracking'),
  ('greedy',              'Greedy'),
  ('stack',               'Stack'),
  ('linked-list',         'Linked List'),
  ('recursion',           'Recursion'),
  ('sorting',             'Sorting'),
  ('heap',                'Heap / Priority Queue')
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Problems seed (10 canonical problems)
-- NOTE: description is NOT NULL; we store a brief excerpt here.
--       Rich markdown content lives in MongoDB (problem_content collection).
-- ---------------------------------------------------------------------------

INSERT INTO problems (slug, title, description, difficulty, acceptance_rate, status)
VALUES ('two-sum', 'Two Sum',
        'Given an array of integers and a target, return indices of the two numbers that add up to target.',
        'EASY', 49.50, 'PUBLISHED')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO problems (slug, title, description, difficulty, acceptance_rate, status)
VALUES ('valid-anagram', 'Valid Anagram',
        'Given two strings s and t, return true if t is an anagram of s.',
        'EASY', 63.20, 'PUBLISHED')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO problems (slug, title, description, difficulty, acceptance_rate, status)
VALUES ('climbing-stairs', 'Climbing Stairs',
        'You are climbing a staircase. It takes n steps to reach the top. Each time you can climb 1 or 2 steps. In how many distinct ways can you climb to the top?',
        'EASY', 51.80, 'PUBLISHED')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO problems (slug, title, description, difficulty, acceptance_rate, status)
VALUES ('binary-search', 'Binary Search',
        'Given a sorted array of integers and a target, return the index of the target or -1 if not found.',
        'EASY', 57.10, 'PUBLISHED')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO problems (slug, title, description, difficulty, acceptance_rate, status)
VALUES ('invert-binary-tree', 'Invert Binary Tree',
        'Given the root of a binary tree, invert the tree and return its root.',
        'EASY', 76.90, 'PUBLISHED')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO problems (slug, title, description, difficulty, acceptance_rate, status)
VALUES ('number-of-islands', 'Number of Islands',
        'Given an m x n 2D binary grid representing a map of land and water, return the number of islands.',
        'MEDIUM', 58.30, 'PUBLISHED')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO problems (slug, title, description, difficulty, acceptance_rate, status)
VALUES ('valid-palindrome', 'Valid Palindrome',
        'A phrase is a palindrome if, after removing non-alphanumeric chars and lowercasing, it reads the same forward and backward.',
        'EASY', 45.20, 'PUBLISHED')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO problems (slug, title, description, difficulty, acceptance_rate, status)
VALUES ('container-with-most-water', 'Container With Most Water',
        'Given an integer array height, find two lines that form a container with the most water.',
        'MEDIUM', 54.70, 'PUBLISHED')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO problems (slug, title, description, difficulty, acceptance_rate, status)
VALUES ('maximum-depth-of-binary-tree', 'Maximum Depth of Binary Tree',
        'Given the root of a binary tree, return its maximum depth.',
        'EASY', 74.50, 'PUBLISHED')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO problems (slug, title, description, difficulty, acceptance_rate, status)
VALUES ('house-robber', 'House Robber',
        'Given an integer array of house money amounts, return the max you can rob without alerting the police (no two adjacent houses).',
        'MEDIUM', 49.90, 'PUBLISHED')
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- problem_topics — link each problem to its topics
-- ---------------------------------------------------------------------------

-- Two Sum → array, hash-table
INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'two-sum' AND t.slug = 'array'
ON CONFLICT DO NOTHING;

INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'two-sum' AND t.slug = 'hash-table'
ON CONFLICT DO NOTHING;

-- Valid Anagram → hash-table, sorting
INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'valid-anagram' AND t.slug = 'hash-table'
ON CONFLICT DO NOTHING;

INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'valid-anagram' AND t.slug = 'sorting'
ON CONFLICT DO NOTHING;

-- Climbing Stairs → dynamic-programming, recursion
INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'climbing-stairs' AND t.slug = 'dynamic-programming'
ON CONFLICT DO NOTHING;

INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'climbing-stairs' AND t.slug = 'recursion'
ON CONFLICT DO NOTHING;

-- Binary Search → array, binary-search
INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'binary-search' AND t.slug = 'array'
ON CONFLICT DO NOTHING;

INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'binary-search' AND t.slug = 'binary-search'
ON CONFLICT DO NOTHING;

-- Invert Binary Tree → tree, recursion
INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'invert-binary-tree' AND t.slug = 'tree'
ON CONFLICT DO NOTHING;

INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'invert-binary-tree' AND t.slug = 'recursion'
ON CONFLICT DO NOTHING;

-- Number of Islands → graph, array
INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'number-of-islands' AND t.slug = 'graph'
ON CONFLICT DO NOTHING;

INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'number-of-islands' AND t.slug = 'array'
ON CONFLICT DO NOTHING;

-- Valid Palindrome → two-pointers
INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'valid-palindrome' AND t.slug = 'two-pointers'
ON CONFLICT DO NOTHING;

-- Container With Most Water → two-pointers, greedy
INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'container-with-most-water' AND t.slug = 'two-pointers'
ON CONFLICT DO NOTHING;

INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'container-with-most-water' AND t.slug = 'greedy'
ON CONFLICT DO NOTHING;

-- Maximum Depth of Binary Tree → tree, recursion
INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'maximum-depth-of-binary-tree' AND t.slug = 'tree'
ON CONFLICT DO NOTHING;

INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'maximum-depth-of-binary-tree' AND t.slug = 'recursion'
ON CONFLICT DO NOTHING;

-- House Robber → dynamic-programming, array
INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'house-robber' AND t.slug = 'dynamic-programming'
ON CONFLICT DO NOTHING;

INSERT INTO problem_topics (problem_id, topic_id)
SELECT p.id, t.id FROM problems p, topics t
WHERE p.slug = 'house-robber' AND t.slug = 'array'
ON CONFLICT DO NOTHING;
