INSERT INTO sysdesign_problems (slug, title, difficulty, category, description_md, requirements) VALUES
('design-url-shortener', 'Design a URL Shortener', 'EASY', 'Web Services',
 '## Design a URL Shortener

Design a service like bit.ly that shortens long URLs.',
 ARRAY['Handle 100M URLs', 'Read-heavy (100:1 read/write)', 'Redirect in < 10ms', 'Analytics tracking']),

('design-twitter', 'Design Twitter', 'HARD', 'Social Media',
 '## Design Twitter

Design a simplified version of Twitter.',
 ARRAY['Post tweets', 'Follow/unfollow users', 'Home timeline', '100M DAU', 'Eventual consistency ok']),

('design-netflix', 'Design Netflix', 'HARD', 'Streaming',
 '## Design Netflix

Design a video streaming platform.',
 ARRAY['Stream video to millions', 'Content recommendation', 'Adaptive bitrate', 'Global CDN']),

('design-rate-limiter', 'Design a Rate Limiter', 'MEDIUM', 'Infrastructure',
 '## Design a Rate Limiter

Design a rate limiter for an API gateway.',
 ARRAY['Token bucket algorithm', 'Distributed system', '< 1ms overhead', 'Multiple rate limit rules']),

('design-chat-system', 'Design a Chat System', 'MEDIUM', 'Messaging',
 '## Design a Chat System

Design a real-time messaging system like WhatsApp.',
 ARRAY['1-on-1 and group chat', 'Online/offline status', 'Message history', '50M DAU']),

('design-search-autocomplete', 'Design Search Autocomplete', 'MEDIUM', 'Search',
 '## Design Search Autocomplete

Design the autocomplete feature for a search engine.',
 ARRAY['Top 5 suggestions', '< 100ms response', '10M QPS', 'Personalization']),

('design-distributed-cache', 'Design a Distributed Cache', 'MEDIUM', 'Infrastructure',
 '## Design a Distributed Cache

Design a distributed caching system like Redis Cluster.',
 ARRAY['High availability', 'Eviction policies', 'Consistent hashing', '1M QPS']),

('design-notification-system', 'Design a Notification System', 'EASY', 'Infrastructure',
 '## Design a Notification System

Design a push notification system.',
 ARRAY['Push/email/SMS', 'Delivery guarantees', '10M notifications/day', 'User preferences'])

ON CONFLICT (slug) DO NOTHING;
